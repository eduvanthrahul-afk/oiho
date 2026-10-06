from __future__ import annotations

import hashlib
import json
import math
import os
import random
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="DecisionTwin Simulation Engine", version="0.1.0")


def setting(name: str, default: str = "") -> str:
    value = os.getenv(name)
    if value is not None:
        return value.strip()
    dotenv = Path(__file__).resolve().parents[2] / ".env"
    try:
        for line in dotenv.read_text(encoding="utf-8").splitlines():
            candidate = line.strip()
            if not candidate or candidate.startswith("#") or "=" not in candidate:
                continue
            key, configured = candidate.split("=", 1)
            if key.strip() == name:
                return configured.strip().strip("\"'")
    except OSError:
        pass
    return default


class Scenario(BaseModel):
    label: str
    price: float = Field(gt=0)


class SimulationRequest(BaseModel):
    decision_id: str
    title: str
    product_description: str = ""
    target_customer: str = ""
    location: str = ""
    market_size: int = Field(default=10000, ge=100, le=10000000)
    current_price: float = Field(default=0, ge=0)
    customer_count: int = Field(default=300, ge=100, le=500)
    scenarios: list[Scenario] = Field(min_length=2, max_length=3)


def stable_seed(request: SimulationRequest) -> int:
    raw = f"{request.decision_id}:{request.title}:{request.market_size}"
    return int(hashlib.sha256(raw.encode()).hexdigest()[:12], 16)


def generate_ai_analysis(request: SimulationRequest, evaluated: list[dict], profile_pairs: list[dict]) -> dict:
    api_key = setting("GROQ_API_KEY")
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="AI_NOT_CONFIGURED: set GROQ_API_KEY on the simulation engine to generate the recommendation and persona discussions.",
        )

    scenario_labels = [row["label"] for row in evaluated]
    pair_ids = [pair["pair_id"] for pair in profile_pairs]
    debate_item_schema = {
        "type": "object",
        "properties": {
            "topic": {"type": "string"},
            "supportive_reply": {"type": "string"},
            "skeptical_reply": {"type": "string"},
        },
        "required": ["topic", "supportive_reply", "skeptical_reply"],
        "additionalProperties": False,
    }
    schema = {
        "type": "object",
        "properties": {
            "recommendation": {"type": "string", "enum": ["LAUNCH", "MODIFY", "DO_NOT_LAUNCH"]},
            "recommended_scenario": {"type": "string", "enum": scenario_labels},
            "confidence": {"type": "integer", "minimum": 0, "maximum": 100},
            "confidence_note": {"type": "string"},
            "biggest_risk": {"type": "string"},
            "most_influential_factor": {"type": "string"},
            "alternative_strategy": {"type": "string"},
            "reasoning": {"type": "array", "items": {"type": "string"}},
            # A keyed object makes every requested exchange mandatory in strict
            # JSON Schema. An unconstrained array could silently omit pairs.
            "debates": {
                "type": "object",
                "properties": {pair_id: debate_item_schema for pair_id in pair_ids},
                "required": pair_ids,
                "additionalProperties": False,
            },
        },
        "required": ["recommendation", "recommended_scenario", "confidence", "confidence_note", "biggest_risk", "most_influential_factor", "alternative_strategy", "reasoning", "debates"],
        "additionalProperties": False,
    }
    context = {
        "decision": {
            "title": request.title,
            "product_description": request.product_description,
            "target_customer": request.target_customer,
            "location": request.location,
            "market_size": request.market_size,
            "current_price": request.current_price,
        },
        "scenario_results": evaluated,
        "persona_pairs": profile_pairs,
    }
    instructions = (
        "You are an analytical decision advisor. Make a fresh recommendation from the supplied scenario data; do not use a fixed rule or always favor the highest revenue/conversion. "
        "Return a recommendation that balances the stated goals, trade-offs, and uncertainty. Cite only values and traits present in the input; do not invent market research, customers, quotes, or facts. "
        "Write each debate as a newly generated, short, respectful exchange between the named simulated personas. Ground each reply in that persona's supplied profile and utility values; do not reuse generic canned wording. "
        "These are fictional AI-generated persona statements, never real customer quotations. Return one exchange for every supplied pair, using its pair_id as the debates object key; do not omit or add IDs. "
        "Give concise decision rationale, biggest uncertainty, the most influential factor, and a practical alternative. Confidence is an uncalibrated AI assessment, not a probability of success."
    )
    body = {
        # This must be the provider's exact model identifier. Keep UI/provenance
        # descriptions separate; putting them here makes the API reject the run.
        "model": setting("GROQ_MODEL", "openai/gpt-oss-120b"),
        "instructions": instructions,
        "input": json.dumps(context, ensure_ascii=False),
        "text": {"format": {"type": "json_schema", "name": "decision_twin_analysis", "strict": True, "schema": schema}},
        "max_output_tokens": 5000,
    }
    req = Request(
        "https://api.groq.com/openai/v1/responses",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "DecisionTwin/0.1 (simulation-engine)",
        },
        method="POST",
    )
    try:
        with urlopen(req, timeout=105) as response:
            payload = json.loads(response.read())
    except HTTPError as error:
        if error.code in (401, 403):
            raise HTTPException(status_code=503, detail="AI_AUTH_FAILED: check the Groq API key configured on the simulation engine.") from error
        if error.code == 429:
            raise HTTPException(status_code=503, detail="AI_RATE_LIMITED: the configured Groq project is rate limited or out of API credits.") from error
        if error.code == 400:
            raise HTTPException(status_code=503, detail="AI_REQUEST_REJECTED: Groq rejected the request. Check GROQ_MODEL and request settings.") from error
        if error.code == 413:
            raise HTTPException(status_code=503, detail="AI_REQUEST_TOO_LARGE: Groq rejected the structured analysis request as too large.") from error
        raise HTTPException(status_code=502, detail=f"AI_PROVIDER_ERROR: Groq returned HTTP {error.code}.") from error
    except (URLError, TimeoutError) as error:
        raise HTTPException(status_code=503, detail="AI_PROVIDER_UNAVAILABLE: could not reach Groq. Retry when the connection is available.") from error

    output_text = "".join(
        part.get("text", "")
        for item in payload.get("output", []) if item.get("type") == "message"
        for part in item.get("content", []) if part.get("type") == "output_text"
    )
    if not output_text:
        raise HTTPException(status_code=502, detail="AI returned no structured analysis. Please retry the simulation.")
    analysis = json.loads(output_text)
    expected_pairs = set(pair_ids)
    returned_pairs = set(analysis["debates"])
    if returned_pairs != expected_pairs:
        raise HTTPException(status_code=502, detail="AI_DISCUSSION_INCOMPLETE: Groq returned an incomplete persona discussion.")
    return analysis


def simulate(request: SimulationRequest) -> dict:
    rng = random.Random(stable_seed(request))
    # One stable synthetic population is reused in every scenario so price is the main changed variable.
    customers = []
    occupations = ["Independent professional", "Small-team operator", "Early-stage founder", "Specialist consultant"]
    target = request.target_customer.lower()
    if "freelanc" in target:
        occupations = ["Independent freelancer", "Independent freelancer", "Project-based consultant", "Solo operator"]
    elif "consult" in target:
        occupations = ["Independent consultant", "Independent consultant", "Specialist advisor", "Solo operator"]
    for index in range(request.customer_count):
        income = max(15000, rng.lognormvariate(math.log(65000), 0.58))
        need = min(1.0, max(0.0, rng.betavariate(2.4, 2.0)))
        trust = min(1.0, max(0.0, rng.betavariate(2.0, 2.6)))
        sensitivity = min(1.0, max(0.0, rng.betavariate(2.2, 2.0)))
        risk = min(1.0, max(0.0, rng.betavariate(1.8, 2.5)))
        customers.append({
            "id": f"CUS-{index + 1:04d}", "age": rng.randint(22, 61), "occupation": rng.choice(occupations),
            "income": income, "need": need, "trust": trust, "sensitivity": sensitivity, "risk": risk,
        })

    evaluated = []
    utility_by_agent: dict[str, dict[str, float]] = {customer["id"]: {} for customer in customers}
    for scenario in request.scenarios:
        willing = 0
        retained = 0
        for customer in customers:
            income, need, trust = customer["income"], customer["need"], customer["trust"]
            sensitivity, risk = customer["sensitivity"], customer["risk"]
            affordability = min(1.0, max(0.0, 1.12 - scenario.price / max(income * 0.025, 1)))
            incumbent_penalty = 0.10 if request.current_price and scenario.price > request.current_price * 1.35 else 0
            utility = (need * 0.42 + trust * 0.19 + risk * 0.12 + affordability * (0.42 + sensitivity * 0.55)
                       - sensitivity * (scenario.price / max(income * 0.012, 1)) * 0.20
                       - incumbent_penalty + rng.gauss(0, 0.075))
            utility_by_agent[customer["id"]][scenario.label] = utility
            if utility > 0.48:
                willing += 1
                # Value fit drives month-three retention; price-sensitive customers churn more.
                if utility - sensitivity * scenario.price / max(income * 0.025, 1) * 0.12 > 0.37:
                    retained += 1
        conversion = willing / len(customers)
        retention = retained / max(willing, 1)
        adoption = round(request.market_size * conversion)
        mrr = round(adoption * scenario.price)
        risk_score = round(min(92, max(12, 100 * (0.47 * (1 - retention) + 0.36 * (1 - conversion) + 0.17 * scenario.price / (scenario.price + 1200)))))
        evaluated.append({
            "label": scenario.label,
            "price": scenario.price,
            "conversion_rate": round(conversion * 100, 1),
            "adoption": adoption,
            "monthly_recurring_revenue": mrr,
            "estimated_annual_revenue": mrr * 12,
            "retention_rate": round(retention * 100, 1),
            "risk_score": risk_score,
            "agents": request.customer_count,
        })

    # The numeric scorecard remains an explicitly synthetic rules model. It provides
    # evidence for the language model, but no longer decides the recommendation.
    reference_scenario = max(evaluated, key=lambda row: row["monthly_recurring_revenue"] * (1 - row["risk_score"] / 250))
    reference_label = reference_scenario["label"]
    agents = []
    segment_groups: dict[str, list[dict]] = {}
    for customer in customers:
        sensitivity, need, trust = customer["sensitivity"], customer["need"], customer["trust"]
        segment = ("High-need value seekers" if need >= 0.68 and sensitivity < 0.58 else
                   "Price-sensitive evaluators" if sensitivity >= 0.68 else
                   "Trust-cautious adopters" if trust < 0.34 else "Balanced evaluators")
        utilities = utility_by_agent[customer["id"]]
        preferred = max(utilities, key=utilities.get)
        utility = utilities[reference_label]
        intent = 1 / (1 + math.exp(-8 * (utility - 0.48)))
        stance = "supportive" if intent >= 0.66 else "skeptical" if intent <= 0.43 else "undecided"
        agent = {
            "id": customer["id"], "kind": "synthetic_customer", "segment": segment,
            "age": customer["age"], "occupation": customer["occupation"],
            "annual_income": round(customer["income"] / 1000) * 1000,
            "need": round(need, 3), "trust": round(trust, 3),
            "price_sensitivity": round(sensitivity, 3), "risk_tolerance": round(customer["risk"], 3),
            "purchase_intent": round(intent, 3), "stance": stance,
            "preferred_scenario": preferred, "scenario_utility": {key: round(value, 3) for key, value in utilities.items()},
        }
        agents.append(agent)
        segment_groups.setdefault(segment, []).append(agent)

    relationships = []
    relationships_by_pair = {}
    def add_relationship(left: dict, right: dict, relation_type: str):
        key = tuple(sorted((left["id"], right["id"])))
        if key[0] == key[1]:
            return None
        if key in relationships_by_pair:
            existing = relationships_by_pair[key]
            if relation_type == "pricing_debate":
                existing["type"] = relation_type
                existing["sentiment"] = "tension"
            return existing
        similarity = 1 - (abs(left["need"] - right["need"]) + abs(left["price_sensitivity"] - right["price_sensitivity"])) / 2
        tension = left["stance"] != right["stance"] and {left["stance"], right["stance"]} >= {"supportive", "skeptical"}
        relationship = {
            "id": f"REL-{len(relationships) + 1:04d}", "source": left["id"], "target": right["id"],
            "type": relation_type, "trust": round((left["trust"] + right["trust"]) / 2, 3),
            "strength": round(max(0.08, similarity), 3), "sentiment": "tension" if tension else "alignment",
        }
        relationships.append(relationship)
        relationships_by_pair[key] = relationship
        return relationship

    # Cohort links express similar modeled constraints; bridge links connect dissimilar viewpoints.
    for group in segment_groups.values():
        group.sort(key=lambda agent: (agent["need"], agent["price_sensitivity"], agent["trust"]))
        for index, agent in enumerate(group):
            if len(group) > 1:
                add_relationship(agent, group[(index + 1) % len(group)], "shared_segment")
    for index in range(0, len(agents), 5):
        left = agents[index]
        candidates = [agent for agent in agents if agent["segment"] != left["segment"] and agent["id"] != left["id"]]
        if candidates:
            right = min(candidates, key=lambda agent: abs(left["need"]-agent["need"])*.45 + abs(left["price_sensitivity"]-agent["price_sensitivity"])*.4 + abs(left["trust"]-agent["trust"])*.15)
            add_relationship(left, right, "cross_segment")

    supportive = [agent for agent in agents if agent["stance"] == "supportive"]
    skeptical = [agent for agent in agents if agent["stance"] == "skeptical"]
    # Pair high and low modeled purchase intent to expose the trade-offs behind the aggregate.
    supportive.sort(key=lambda agent: agent["purchase_intent"], reverse=True)
    skeptical.sort(key=lambda agent: agent["purchase_intent"])
    profile_pairs = []
    # Keep strict structured output compact enough for Groq, while the graph
    # itself still contains the full modeled population and relationship set.
    pair_count = min(8, len(supportive), len(skeptical))
    for index in range(pair_count):
        advocate, skeptic = supportive[index], skeptical[index]
        relationship = add_relationship(advocate, skeptic, "pricing_debate")
        profile_pairs.append({
            "pair_id": f"DEB-{index + 1:03d}", "relationship_id": relationship["id"],
            "supportive_profile": {key: advocate[key] for key in ("id", "segment", "occupation", "need", "trust", "price_sensitivity", "risk_tolerance", "scenario_utility")},
            "skeptical_profile": {key: skeptic[key] for key in ("id", "segment", "occupation", "need", "trust", "price_sensitivity", "risk_tolerance", "scenario_utility")},
        })

    analysis = generate_ai_analysis(request, evaluated, profile_pairs)
    selected_scenario = next(row for row in evaluated if row["label"] == analysis["recommended_scenario"])
    pair_by_id = {pair["pair_id"]: pair for pair in profile_pairs}
    debates = []
    for pair_id, generated in analysis["debates"].items():
        pair = pair_by_id[pair_id]
        advocate, skeptic = pair["supportive_profile"], pair["skeptical_profile"]
        debates.append({
            "id": pair_id, "relationship_id": pair["relationship_id"],
            "topic": generated["topic"], "resolution": "unresolved_tradeoff",
            "messages": [
                {"agent_id": advocate["id"], "stance": "supportive", "text": generated["supportive_reply"],
                 "evidence": {"need": advocate["need"], "price_sensitivity": advocate["price_sensitivity"]}},
                {"agent_id": skeptic["id"], "stance": "skeptical", "text": generated["skeptical_reply"],
                 "evidence": {"need": skeptic["need"], "price_sensitivity": skeptic["price_sensitivity"]}},
            ],
        })

    graph = {
        "agent_count": len(agents), "relationship_count": len(relationships), "debate_count": len(debates),
        "segments": [{"name": name, "count": len(group)} for name, group in sorted(segment_groups.items())],
        "model": setting("GROQ_MODEL", "openai/gpt-oss-120b"),
    }
    return {
        "recommendation": analysis["recommendation"],
        "recommended_scenario": selected_scenario["label"],
        "recommended_price": selected_scenario["price"],
        "confidence": analysis["confidence"],
        "confidence_note": analysis["confidence_note"],
        "evidence_quality": "AI-generated judgment over a synthetic, rule-based scorecard. Personas and dialogue are fictional; no real customers or interviews were used.",
        "biggest_risk": analysis["biggest_risk"],
        "most_influential_factor": analysis["most_influential_factor"],
        "alternative_strategy": analysis["alternative_strategy"],
        "reasoning": analysis["reasoning"],
        "scenarios": evaluated,
        "population": {"size": request.customer_count, "market_size": request.market_size, "source": "synthetic behavioral model"},
        "market_graph": graph,
        "analysis_provider": "Groq",
        "analysis_model": setting("GROQ_MODEL", "openai/gpt-oss-120b"),
        "discussion_source": "AI-generated dialogue between synthetic personas; not real interviews",
        "agents": agents,
        "relationships": relationships,
        "discussions": debates,
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "ai_analysis": {
            "configured": bool(setting("GROQ_API_KEY")),
            "provider": "Groq",
            "model": setting("GROQ_MODEL", "openai/gpt-oss-120b"),
        },
    }


@app.post("/simulate")
def run_simulation(request: SimulationRequest):
    return simulate(request)

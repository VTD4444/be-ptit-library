"""Chuyen nhan cham theo chi so pool (judgments_*.py) thanh eval/qrels.tsv (query_id, document_id, grade)."""
import json

from eval import judgments_AE, judgments_F
from eval.run_eval import MODES, OUT, ROOT, load_queries, titles


def main() -> None:
    runs = json.loads((OUT / "runs.json").read_text(encoding="utf-8"))
    names = titles()
    grades = {**judgments_AE.GRADES, **judgments_F.GRADES}
    outside = {**judgments_AE.OUTSIDE_POOL, **getattr(judgments_F, "OUTSIDE_POOL", {})}
    lines = ["# query_id\tdocument_id\tgrade\ttitle (Claude cham theo pooling top-10 cua 3 che do; can nguoi ra soat)"]
    for q in load_queries():
        pool: dict[str, None] = {}
        for mode in MODES:
            for r in runs[f"{q['id']}|{mode}"]["results"]:
                pool.setdefault(r["documentId"], None)
        given = grades[q["id"]]
        for i, doc in enumerate(pool):
            lines.append(f"{q['id']}\t{doc}\t{given.get(i, 0)}\t{names.get(doc, '?')}")
        for prefix, grade in outside.get(q["id"], {}).items():
            doc = next(d for d in names if d.startswith(prefix))
            if doc not in pool:
                lines.append(f"{q['id']}\t{doc}\t{grade}\t{names[doc]} (ngoai pool)")
    (ROOT / "qrels.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(len(lines) - 1, "dong qrels")


if __name__ == "__main__":
    main()

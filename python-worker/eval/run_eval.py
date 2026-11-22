"""
Danh gia dinh luong tim kiem noi dung (TF-IDF / vector / hybrid).

Buoc:
  python -m eval.run_eval run     # goi worker, luu eval/out/runs.json (+ thoi gian phan hoi)
  python -m eval.run_eval pool    # xuat eval/out/pool.tsv: cac cap (truy van, tai lieu) can cham
  python -m eval.run_eval score   # doc eval/qrels.tsv, tinh chi so, ghi bao cao Markdown

qrels.tsv: query_id <TAB> document_id <TAB> grade (0 khong lien quan, 1 lien quan, 2 tra loi truc tiep).
Tai lieu khong co trong qrels duoc coi la grade 0 (chuan pooling TREC).
"""
import json
import math
import random
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.stats import wilcoxon

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
WORKER = "http://127.0.0.1:8000"
MODES = ("tfidf", "vector", "hybrid")
K = 10
REPEATS = 5
GROUPS = {
    "A": "Tên riêng / từ hiếm",
    "B": "Thuật ngữ chuyên ngành",
    "C": "Câu hỏi tự nhiên",
    "D": "Tiếng Anh (đa ngôn ngữ)",
    "E": "Đoạn văn dài",
    "F": "Không có đáp án trong kho",
}


def load_queries() -> list[dict]:
    return [json.loads(line) for line in (ROOT / "queries.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]


def titles() -> dict[str, str]:
    return json.loads((ROOT / "titles.json").read_text(encoding="utf-8"))


def call(query: str, mode: str) -> tuple[dict, float]:
    body = json.dumps({"query": query, "limit": K, "bestPerDocument": True, "searchMode": mode,
                       "maxLocations": 1}).encode()
    req = urllib.request.Request(WORKER + "/api/search", data=body, headers={"Content-Type": "application/json"})
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, timeout=600) as resp:
        data = json.loads(resp.read())
    return data, (time.perf_counter() - t0) * 1000


def keep_awake() -> None:
    """Windows: khong cho may ngu (Modern Standby) trong luc do, neu khong request se treo toi khi may thuc."""
    if sys.platform == "win32":
        import ctypes
        ctypes.windll.kernel32.SetThreadExecutionState(0x80000000 | 0x00000001)  # ES_CONTINUOUS | ES_SYSTEM_REQUIRED


def cmd_run() -> None:
    OUT.mkdir(exist_ok=True)
    keep_awake()
    queries = load_queries()
    # Doi model embedding nap xong (worker nap nen sau khi khoi dong); khong lam nong cache copy_store
    for _ in range(60):
        try:
            call("khởi động", "vector")
            break
        except urllib.error.HTTPError:
            time.sleep(2)
    path = OUT / "runs.json"
    runs = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}  # chay tiep phan con thieu
    for q in queries:
        for mode in MODES:
            if f"{q['id']}|{mode}" in runs:
                continue
            latencies, first = [], None
            for _ in range(REPEATS):
                data, ms = call(q["query"], mode)
                latencies.append(ms)
                first = first or data
            runs[f"{q['id']}|{mode}"] = {
                "usedMode": first["searchMode"],
                "latencies": latencies,
                "results": [{"documentId": p["documentId"], "score": p["score"], "page": p["pageStart"],
                             "snippet": p["snippet"]} for p in first["passages"]],
            }
            print(q["id"], mode, len(first["passages"]), f"{statistics.median(latencies):.0f} ms", flush=True)
        path.write_text(json.dumps(runs, ensure_ascii=False, indent=1), encoding="utf-8")


def cmd_pool() -> None:
    runs = json.loads((OUT / "runs.json").read_text(encoding="utf-8"))
    names = titles()
    lines = []
    for q in load_queries():
        seen = {}
        for mode in MODES:
            for r in runs[f"{q['id']}|{mode}"]["results"]:
                seen.setdefault(r["documentId"], r)
        lines.append(f"### {q['id']} {q['query'][:160]}")
        for doc, r in seen.items():
            snippet = r["snippet"].replace("<mark>", "").replace("</mark>", "").replace("\n", " ")[:170]
            lines.append(f"{q['id']}\t{doc}\t{names.get(doc, '?')}\t{snippet}")
    (OUT / "pool.tsv").write_text("\n".join(lines), encoding="utf-8")
    print(sum(1 for line in lines if not line.startswith("#")), "cap can cham")


# ---------------------------------------------------------------- chi so

def dcg(gains: list[int]) -> float:
    return sum((2 ** g - 1) / math.log2(i + 2) for i, g in enumerate(gains))


def metrics_for(ranked: list[str], rel: dict[str, int]) -> dict:
    gains = [rel.get(d, 0) for d in ranked[:K]]
    ideal = sorted(rel.values(), reverse=True)[:K]
    relevant = {d for d, g in rel.items() if g >= 1}
    first = next((i for i, g in enumerate(gains) if g >= 1), None)
    return {
        "ndcg": dcg(gains) / dcg(ideal) if ideal and dcg(ideal) > 0 else 0.0,
        "mrr": 1 / (first + 1) if first is not None else 0.0,
        "s1": float(bool(gains) and gains[0] >= 1),
        "s3": float(any(g >= 1 for g in gains[:3])),
        "p5": sum(1 for g in gains[:5] if g >= 1) / 5,
        "recall": len(relevant & set(ranked[:K])) / len(relevant) if relevant else 0.0,
    }


def bootstrap_ci(diffs: list[float], n: int = 10000, seed: int = 7) -> tuple[float, float]:
    rnd = random.Random(seed)
    means = sorted(statistics.fmean(rnd.choices(diffs, k=len(diffs))) for _ in range(n))
    return means[int(0.025 * n)], means[int(0.975 * n)]


def pct(values: list[float], p: float) -> float:
    return float(np.percentile(values, p))


def coverage() -> dict:
    from app import config
    from app.tfidf_index import TfidfIndex
    index = TfidfIndex.load(config.MODEL_DIR)
    vectors = set(np.load(config.DATA_DIR / "vectors" / "embeddings.npz", allow_pickle=True)["ids"].tolist())
    missing_by_doc = defaultdict(int)
    for row, chunk_id in enumerate(index.chunk_ids):
        if chunk_id not in vectors:
            missing_by_doc[index.copies[index.chunk_copy[row]]["documentId"]] += 1
    total = len(index.chunk_ids)
    missing = sum(missing_by_doc.values())
    return {"total": total, "missing": missing, "docs_missing": dict(missing_by_doc),
            "documents": len({c["documentId"] for c in index.copies})}


def cmd_score(report_path: str) -> None:
    runs = json.loads((OUT / "runs.json").read_text(encoding="utf-8"))
    qrels: dict[str, dict[str, int]] = defaultdict(dict)
    for line in (ROOT / "qrels.tsv").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            qid, doc, grade = line.split("\t")[:3]
            qrels[qid][doc] = int(grade)
    queries = load_queries()
    names = titles()
    # Nhom F duoc thiet ke la "kho khong co tai lieu tra loi"; tai lieu chi nhac thoang (grade 1) van ghi trong qrels
    answerable = [q for q in queries if q["group"] != "F"]
    no_answer = [q for q in queries if q not in answerable]

    per = {mode: {q["id"]: metrics_for([r["documentId"] for r in runs[f"{q['id']}|{mode}"]["results"]], qrels[q["id"]])
                  for q in answerable} for mode in MODES}
    keys = [("ndcg", "nDCG@10"), ("mrr", "MRR@10"), ("s1", "Success@1"), ("s3", "Success@3"),
            ("p5", "Precision@5"), ("recall", "Recall@10")]

    def avg(mode, key, ids):
        return statistics.fmean(per[mode][i][key] for i in ids) if ids else float("nan")

    out = []
    w = out.append
    cov = coverage()
    w("# Đánh giá định lượng tìm kiếm nội dung (TF-IDF / Vector / Hybrid)")
    w("")
    w(f"Ngày chạy: {time.strftime('%d/%m/%Y %H:%M')} · Worker `{WORKER}` · {len(queries)} truy vấn · "
      f"top-{K}, mỗi tài liệu một đoạn (`bestPerDocument=true`) · thời gian = trung vị/percentile của {REPEATS} lần gọi")
    w("")
    w("## Tóm tắt")
    w("")
    all_ids = [q["id"] for q in answerable]
    best = max(MODES, key=lambda m: avg(m, "ndcg", all_ids))
    w(f"- Chế độ có nDCG@10 cao nhất trên {len(answerable)} truy vấn có đáp án: **{best.upper()}** "
      f"({avg(best, 'ndcg', all_ids):.3f}).")
    for mode in MODES:
        lat = [x for q in queries for x in runs[f"{q['id']}|{mode}"]["latencies"]]
        w(f"- {mode.upper()}: nDCG@10 = {avg(mode, 'ndcg', all_ids):.3f}, Success@3 = {avg(mode, 's3', all_ids):.0%}, "
          f"Precision@5 = {avg(mode, 'p5', all_ids):.0%}, p95 = {pct(lat, 95):.0f} ms")
    fp = {m: sum(1 for q in no_answer if runs[f"{q['id']}|{m}"]["results"]) for m in MODES}
    w(f"- Câu không có đáp án ({len(no_answer)} câu) vẫn trả kết quả: "
      + ", ".join(f"{m.upper()} {fp[m]}/{len(no_answer)}" for m in MODES) + ".")
    w(f"- Độ phủ embedding: {cov['total'] - cov['missing']:,}/{cov['total']:,} chunk "
      f"({(cov['total'] - cov['missing']) / cov['total']:.1%}); {len(cov['docs_missing'])} tài liệu thiếu embedding.")
    w("")

    if (ROOT / "analysis.md").exists():  # phan tich + de xuat viet tay, chen sau tom tat
        w((ROOT / "analysis.md").read_text(encoding="utf-8"))
        w("")
    w("## 1. Chất lượng xếp hạng (truy vấn có đáp án)")
    w("")
    w("| Chỉ số | " + " | ".join(m.upper() for m in MODES) + " |")
    w("|---|" + "---:|" * len(MODES))
    for key, label in keys:
        w(f"| {label} | " + " | ".join(f"{avg(m, key, all_ids):.3f}" for m in MODES) + " |")
    w("")

    w("### nDCG@10 theo nhóm truy vấn")
    w("")
    w("| Nhóm | Số câu | " + " | ".join(m.upper() for m in MODES) + " | Tốt nhất |")
    w("|---|---:|" + "---:|" * len(MODES) + "---|")
    for g, label in GROUPS.items():
        ids = [q["id"] for q in answerable if q["group"] == g]
        if not ids:
            continue
        vals = {m: avg(m, "ndcg", ids) for m in MODES}
        w(f"| {g}. {label} | {len(ids)} | " + " | ".join(f"{vals[m]:.3f}" for m in MODES)
          + f" | {max(vals, key=vals.get).upper()} |")
    w("")
    w("### Precision@5 theo nhóm (mức \"độn\" kết quả không liên quan)")
    w("")
    w("| Nhóm | " + " | ".join(m.upper() for m in MODES) + " |")
    w("|---|" + "---:|" * len(MODES))
    for g, label in GROUPS.items():
        ids = [q["id"] for q in answerable if q["group"] == g]
        if ids:
            w(f"| {g}. {label} | " + " | ".join(f"{avg(m, 'p5', ids):.0%}" for m in MODES) + " |")
    w("")

    w("## 2. Kiểm định ý nghĩa thống kê (ghép cặp theo truy vấn, nDCG@10)")
    w("")
    w("| So sánh | Chênh lệch TB | KTC 95% (bootstrap) | p (Wilcoxon) | Kết luận |")
    w("|---|---:|---|---:|---|")
    for a, b in (("hybrid", "tfidf"), ("hybrid", "vector"), ("vector", "tfidf")):
        diffs = [per[a][i]["ndcg"] - per[b][i]["ndcg"] for i in all_ids]
        lo, hi = bootstrap_ci(diffs)
        nz = [d for d in diffs if abs(d) > 1e-12]
        p = wilcoxon(nz).pvalue if len(nz) >= 5 else float("nan")
        verdict = ("có ý nghĩa" if p < 0.05 else "chưa đủ bằng chứng") if not math.isnan(p) else "quá ít cặp khác nhau"
        w(f"| {a.upper()} − {b.upper()} | {statistics.fmean(diffs):+.3f} | [{lo:+.3f}, {hi:+.3f}] | {p:.3f} | {verdict} |")
    w("")
    for g, label in GROUPS.items():
        ids = [q["id"] for q in answerable if q["group"] == g]
        if len(ids) < 2:
            continue
        diffs = [per["hybrid"][i]["ndcg"] - per["tfidf"][i]["ndcg"] for i in ids]
        lo, hi = bootstrap_ci(diffs)
        w(f"- Nhóm {g} ({label}): HYBRID − TFIDF = {statistics.fmean(diffs):+.3f}, KTC 95% [{lo:+.3f}, {hi:+.3f}]")
    w("")

    w("## 3. Câu hỏi không có đáp án trong kho")
    w("")
    w("Lý tưởng: không trả kết quả, hoặc điểm top-1 thấp rõ rệt so với câu có đáp án để có thể đặt ngưỡng cắt.")
    w("")
    w("| Mã | Truy vấn | " + " | ".join(f"{m.upper()} (số KQ / điểm top-1)" for m in MODES) + " |")
    w("|---|---|" + "---|" * len(MODES))
    for q in no_answer:
        cells = []
        for m in MODES:
            res = runs[f"{q['id']}|{m}"]["results"]
            cells.append(f"{len(res)} / {res[0]['score']:.3f}" if res else "0 / –")
        w(f"| {q['id']} | {q['query'][:50]} | " + " | ".join(cells) + " |")
    w("")
    w("Phân bố điểm top-1 (để chọn ngưỡng):")
    w("")
    w("| Chế độ | Có đáp án: trung vị [min–max] | Không đáp án: trung vị [min–max] | Tách được? |")
    w("|---|---|---|---|")
    for m in MODES:
        a = [runs[f"{q['id']}|{m}"]["results"][0]["score"] for q in answerable if runs[f"{q['id']}|{m}"]["results"]]
        n = [runs[f"{q['id']}|{m}"]["results"][0]["score"] for q in no_answer if runs[f"{q['id']}|{m}"]["results"]]
        sep = "–"
        if a and n:
            sep = "có" if max(n) < min(a) else f"chồng lấn ({sum(1 for x in a if x <= max(n))}/{len(a)} câu có đáp án ≤ max không-đáp-án)"
        fmt = lambda v: f"{statistics.median(v):.3f} [{min(v):.3f}–{max(v):.3f}]" if v else "–"
        w(f"| {m.upper()} | {fmt(a)} | {fmt(n)} | {sep} |")
    w("")
    w("Ghi chú: điểm HYBRID là RRF chuẩn hoá theo thứ hạng (đứng đầu cả hai luồng = 1,0), không phải xác suất liên quan.")
    w("")

    w("## 4. Hiệu năng (thời gian tại worker, ms)")
    w("")
    w(f"Lần gọi đầu của mỗi (truy vấn, chế độ) thường phải nạp dữ liệu bản số từ đĩa (cache `copy_store` chưa có); "
      f"{REPEATS - 1} lần sau là cache nóng.")
    w("")
    w("| Chế độ | Lần đầu p50 | Lần đầu p95 | Lần đầu max | Cache nóng p50 | Cache nóng p95 | > 30 s |")
    w("|---|---:|---:|---:|---:|---:|---:|")
    for m in MODES:
        cold = [runs[f"{q['id']}|{m}"]["latencies"][0] for q in queries]
        warm = [x for q in queries for x in runs[f"{q['id']}|{m}"]["latencies"][1:]]
        alls = cold + warm
        w(f"| {m.upper()} | {pct(cold, 50):.0f} | {pct(cold, 95):.0f} | {max(cold):.0f} | {pct(warm, 50):.0f} | "
          f"{pct(warm, 95):.0f} | {sum(1 for x in alls if x > 30000)} |")
    w("")
    w("| Nhóm | " + " | ".join(f"{m.upper()} nóng p50" for m in MODES) + " |")
    w("|---|" + "---:|" * len(MODES))
    for g, label in GROUPS.items():
        ids = [q["id"] for q in queries if q["group"] == g]
        w(f"| {g}. {label} | " + " | ".join(
            f"{pct([x for i in ids for x in runs[f'{i}|{m}']['latencies'][1:]], 50):.0f}" for m in MODES) + " |")
    w("")
    extra = ROOT / "out" / "resources.md"
    if extra.exists():
        w(extra.read_text(encoding="utf-8"))
        w("")

    w("## 5. Độ phủ dữ liệu")
    w("")
    w(f"- Chunk trong chỉ mục TF-IDF: {cov['total']:,} (thuộc {cov['documents']} tài liệu)")
    w(f"- Chunk có embedding: {cov['total'] - cov['missing']:,} ({(cov['total'] - cov['missing']) / cov['total']:.1%}); "
      f"thiếu {cov['missing']:,}")
    w("")
    w("| Tài liệu thiếu embedding | Số chunk thiếu |")
    w("|---|---:|")
    for doc, n in sorted(cov["docs_missing"].items(), key=lambda kv: -kv[1]):
        w(f"| {names.get(doc, doc)} | {n} |")
    w("")

    w("## 6. Chi tiết từng truy vấn (nDCG@10)")
    w("")
    w("| Mã | Truy vấn | Số TL liên quan | " + " | ".join(m.upper() for m in MODES) + " |")
    w("|---|---|---:|" + "---:|" * len(MODES))
    for q in answerable:
        n_rel = sum(1 for g in qrels[q["id"]].values() if g >= 1)
        w(f"| {q['id']} | {q['query'][:60]}{'…' if len(q['query']) > 60 else ''} | {n_rel} | "
          + " | ".join(f"{per[m][q['id']]['ndcg']:.2f}" for m in MODES) + " |")
    w("")

    w("## 7. Phương pháp và giới hạn")
    w("")
    w(f"- Bộ truy vấn: `python-worker/eval/queries.jsonl`, 6 nhóm × 8 câu. Không đưa truy vấn Boolean vào so sánh "
      "vì cả ba chế độ đều tự chuyển sang TF-IDF.")
    w(f"- Nhãn liên quan (`python-worker/eval/qrels.tsv`): gộp top-{K} của cả ba chế độ (pooling), chấm theo tài liệu "
      "ở 3 mức 0/1/2 dựa trên tên tài liệu và đoạn trích. **Nhãn do Claude chấm, cần người kiểm tra lại** "
      "trước khi dùng làm số liệu chính thức. Tài liệu ngoài pool coi là không liên quan, nên Recall là cận trên.")
    w("- Chỉ số tính ở mức tài liệu, chưa đo độ chính xác định vị trang (cần nhãn trang).")
    w("- Thời gian đo trên máy dev (CPU), gọi trực tiếp worker, chưa gồm Spring và mạng.")
    w(f"- Chạy lại: `cd python-worker && .venv/Scripts/python.exe -m eval.run_eval run && "
      f".venv/Scripts/python.exe -m eval.run_eval score <đường dẫn báo cáo>`")
    Path(report_path).write_text("\n".join(out) + "\n", encoding="utf-8")
    print("Da ghi", report_path)


if __name__ == "__main__":
    {"run": cmd_run, "pool": cmd_pool}.get(sys.argv[1], lambda: None)() if sys.argv[1] != "score" \
        else cmd_score(sys.argv[2] if len(sys.argv) > 2 else str(OUT / "report.md"))

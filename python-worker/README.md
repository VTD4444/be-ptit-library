# Python Worker (bóc tách PDF, lập chỉ mục & tìm kiếm văn bản)

Service FastAPI chạy cạnh Spring Boot, cài đặt theo hai tài liệu:
- **"XỬ LÝ VĂN BẢN"**: TF-IDF (unigram + bigram, sublinear TF, smooth IDF, chuẩn hoá L2, cắt tỉa từ điển) + cosine.
- **Lecture 8 – Text indexing and Boolean query model**: tiền xử lý (có gom đồng nghĩa), inverted file, positional index, truy vấn Boolean AND/OR/NOT, cụm từ.

Ngoài TF-IDF còn có tìm theo nghĩa bằng embedding (pgvector) và chế độ lai, xem mục [Chế độ tìm kiếm](#chế-độ-tìm-kiếm-tfidf--vector--hybrid).

Kho chủ yếu tiếng Việt nên có điều chỉnh (xem dưới). Không có OCR.
Spring gọi sang qua `app.python-worker.base-url` (mặc định `http://localhost:8000`).

| Endpoint | Gọi từ | Việc làm |
| --- | --- | --- |
| `GET /health` | `PythonWorkerClient.isWorkerHealthy` | Kiểm tra sống + kích thước mô hình |
| `POST /api/extract-index` | Upload PDF (`/api/admin/documents/{id}/digital-copies`) | Nhận job, trả 202, xử lý ngầm |
| `POST /api/search` | `/api/search/smart`, `/api/search/document/{id}/matches` | Lọc Boolean → xếp hạng cosine → gộp chunk gối nhau → định vị |
| `POST /api/index/rebuild` | Thủ công | Lập chỉ mục lại (sau khi xoá tài liệu, sửa từ điển đồng nghĩa...) |
| `GET /api/embeddings/status` | Thủ công | Model đã nạp chưa, bao nhiêu chunk đã có embedding |
| `POST /api/embeddings/backfill[?force=true]` | Thủ công | Sinh embedding cho chunk còn thiếu (trả 202, chạy ngầm); `force=true` sinh lại hết, dùng sau khi đổi model |

## Dữ liệu (`DATA_DIR`, mặc định `./data`)

```
data/
  copies/{digital_copy_id}.json.gz   # mỗi bản số: dòng từ chuẩn + positional index + khoảng của từng chunk
  tfidf/
    vocabulary.json  idf.npy  matrix.npz  documents.json   # 4 thành phần mục 1.7 (XLVB)
    inverted.json                                          # inverted file: term -> các bản số chứa term
```

**Dòng từ chuẩn**: mỗi bản số có một dãy từ duy nhất, mỗi từ mang trang, đoạn, câu, dòng, toạ độ trên trang
(Lecture 8: `term → (record, paragraph, sentence, word no.)`). Token (sau tách từ) trỏ về khoảng từ gốc `[start, end]`;
chunk chỉ là khoảng `[start, end)` trên dãy này.

## Chunk gối nhau → đoạn thống nhất

Chunk được chia có `overlap` để không cắt mất ngữ cảnh, nên một chỗ khớp trong vùng gối thuộc **cả hai chunk**.
Nếu trả về từng chunk thì kết quả bị trùng và vị trí lệch nhau. Khi tìm kiếm:
1. Chấm điểm cosine cho từng chunk (mỗi chunk là một văn bản dⱼ của TF-IDF).
2. Với cùng một bản số, các chunk khớp có khoảng chồng nhau được **gộp thành một đoạn** `[wordStart, wordEnd)`;
   điểm đoạn = điểm cao nhất (giống OR có trọng số – Lecture 8).
3. Định vị trên đoạn đã gộp nên mỗi vị trí chỉ xuất hiện **một lần**.

## Định vị

Trong mỗi đoạn, dùng positional index tìm từng từ / cụm từ của truy vấn (sau tách từ + đồng nghĩa), gộp các từ liền kề
thành một vùng, tách vùng vắt qua trang. Mỗi vị trí trả về: `page`, `printedPageLabel`, `paragraph`, `sentence`,
`wordStart`, `wordEnd`, `text`, `rects` (toạ độ điểm PDF, gộp theo dòng) và `pageWidth`/`pageHeight` để frontend
vẽ highlight lên trang (chia tỷ lệ theo kích thước canvas).

## Truy vấn

- Câu hỏi / đoạn văn bình thường → chỉ xếp hạng TF-IDF + cosine.
- Có toán tử thì lọc Boolean trên inverted file trước, rồi mới xếp hạng (hybrid – slide cuối Lecture 8):
  - `AND`, `OR`, `NOT` (viết HOA), ngoặc `( )`; hai từ đứng cạnh nhau = AND ngầm định.
  - `"cụm từ"`: các token phải liền kề đúng thứ tự (kiểm tra bằng positional index).
  - Ví dụ: `"mạng nơ-ron" AND (ảnh OR video) NOT y tế`.
- Phần phủ định (`NOT ...`) không dùng để xếp hạng hay highlight.

## Chế độ tìm kiếm (tfidf / vector / hybrid)

Bước xếp hạng chunk có 3 chế độ. Mỗi request chọn qua `searchMode`; bỏ trống thì dùng `SEARCH_MODE` trong `.env` (mặc định `tfidf`).

| Chế độ | Cách xếp hạng | Điểm trả về |
| --- | --- | --- |
| `tfidf` | Cosine TF-IDF như trên | cosine TF-IDF, lọc theo `minScore` |
| `vector` | Cosine giữa embedding truy vấn và embedding chunk (pgvector, toán tử `<=>`) | cosine embedding, lọc theo `VECTOR_MIN_SCORE` |
| `hybrid` | Lấy `HYBRID_CANDIDATE_CHUNKS` chunk tốt nhất của mỗi luồng, gộp bằng **Reciprocal Rank Fusion**: `Σ 1/(RRF_K + hạng)` | điểm RRF chia cho điểm tối đa, nằm trong [0, 1] |

- Truy vấn Boolean luôn dùng `tfidf` (vector không hiểu `NOT` và `"cụm từ"`).
- `hybrid` vẫn trả kết quả TF-IDF khi model chưa nạp xong hoặc DB lỗi; `vector` thì báo lỗi. Response có `searchMode` là chế độ thực sự đã dùng.
- Khớp theo nghĩa có thể không chứa từ nào của truy vấn: khi đó vị trí trả về là cả đoạn (để vẫn vẽ được trên trang) và không có `highlightTerms`.
- Gộp chunk thành đoạn và định vị giống hệt chế độ `tfidf`.

**Embedding**
- Model mặc định `intfloat/multilingual-e5-base` (768 chiều, chạy CPU, có tiền tố `query: ` / `passage: ` của họ E5). Lần chạy đầu tải model mất vài phút; model được nạp ở luồng riêng nên TF-IDF vẫn dùng được ngay.
- Embedding được sinh ở cuối luồng bóc tách (bước 7 dưới). Lỗi sinh embedding không làm hỏng job: tài liệu vẫn tìm được bằng TF-IDF và có ghi chú.
- Chunk đã có từ trước khi bật embedding: gọi `POST /api/embeddings/backfill` một lần.
- Đổi model khác số chiều: sửa `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`, đổi kiểu cột `embedding vector(N)` rồi gọi `POST /api/embeddings/backfill?force=true`.

**Hạn chế hiện tại**
- **Chưa có index ANN (HNSW/IVFFlat) trên cột `embedding`**: tìm vector đang quét tuần tự toàn bảng. Kho lớn thì cần tạo, ví dụ:
  `CREATE INDEX ON public.search_document_chunks USING hnsw (embedding vector_cosine_ops);`
- `VECTOR_MIN_SCORE`, `HYBRID_CANDIDATE_CHUNKS`, `RRF_K` đang là giá trị tạm, chưa qua đánh giá trên dữ liệu thật.

## Ánh xạ với tài liệu

| Nội dung | Cài đặt |
| --- | --- |
| Loại phần dư thừa (XLVB 1.3 B1) | `pdf_extract.py`: bỏ header/footer lặp ≥ 50% số trang, dòng số trang, trang mục lục / danh sách hình (dấu chấm dẫn `. . .`) |
| Tách từ, chữ thường, bỏ số/dấu câu, từ dừng | `text_preprocess.py`, `stopwords.py` |
| Stemming/lemma | Chỉ cho từ tiếng Anh xen lẫn (`networks → network`); tiếng Việt không biến hình |
| Gom đồng nghĩa (Lecture 8) | `synonyms.py` + `synonyms.txt` (viết tự nhiên: `cơ sở dữ liệu: csdl, database`), thay theo **chuỗi token** vì một khái niệm tiếng Việt thường là nhiều token; áp dụng khi sinh đặc trưng, lọc Boolean & định vị |
| Unigram + bigram, min_df/max_df/max_features | `tfidf_index._fit` |
| TF-IDF, sublinear TF, smooth IDF, L2 | `TfidfVectorizer(sublinear_tf, smooth_idf, norm="l2")` – theo XLVB (Lecture 8 dùng `tf·log(N/df)` thô; nhóm chọn bản cải tiến của XLVB) |
| Inverted file, Boolean AND/OR/NOT | `tfidf_index.inverted` + `searcher._evaluate` (AND giao từ postings ngắn nhất trước, NOT = phần bù trên toàn kho) |
| Positional index, cụm từ, vị trí | `copy_store.py` + `searcher._find_spans/_locations` |
| s = X·qᵀ, top-n | `TfidfIndex.search_chunks` |

### Điều chỉnh cho tiếng Việt
- **Tách từ bằng pyvi** theo từng câu: tiếng Việt viết cách theo âm tiết, một từ thường gồm nhiều âm tiết
  (`học máy → học_máy`). Token vẫn trỏ về đúng các từ gốc nên định vị không bị lệch.
- **Giữ dấu thanh** (không bỏ dấu tiếng Việt): bỏ dấu làm `bàn/bán/bạn` trùng nhau.
- Từ dừng gồm cả từ ghép (`tuy_nhiên`, `vì_vậy`, ...); `max_df` cắt nốt từ quá phổ biến.

## Luồng bóc tách

1. Spring lưu PDF, tạo `digital_copies` (PENDING) + `background_jobs` (QUEUED), **sau khi commit** mới gọi `/api/extract-index`.
2. Worker: job → RUNNING, bản số → PROCESSING.
3. PyMuPDF đọc từng **ký tự** kèm toạ độ rồi tự ghép thành từ theo khoảng trống (PDF xuất từ LaTeX tiếng Việt không ghi dấu cách sau chữ có dấu chồng: `Hệcơ sởdữliệu`); bỏ header/footer và trang mục lục; chương/mục lấy từ bookmark, không có thì nhận diện `Chương 1`, `1.2 ...`.
4. Tách từ theo câu → positional index; chia chunk theo khoảng từ (không cắt ngang chương/mục); lưu `data/copies/...`.
5. Lưu chunk vào `search_document_chunks` (kèm `fts_tokens` để Spring tìm dự phòng khi worker tắt).
6. **Lập chỉ mục TF-IDF + inverted file lại trên toàn kho** (từ điển, IDF phụ thuộc mọi văn bản); token lấy từ positional index nên không phải tách từ lại.
7. Sinh embedding cho các chunk mới (lỗi thì bỏ qua, chỉ ghi chú vào bản số).
8. Bản số → INDEXED / LOW_QUALITY, job → COMPLETED. Lỗi bất kỳ → job FAILED + `error_message`.

## Chạy

Yêu cầu Python 3.10+.

```bash
cd python-worker
python -m venv .venv
.venv\Scripts\activate          # Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env          # rồi điền DATABASE_URL
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Test: `pip install pytest && python -m pytest tests`

Lưu ý:
- Worker đọc PDF theo đường dẫn tuyệt đối Spring gửi sang, nên phải chạy **cùng máy** (hoặc ổ đĩa chia sẻ) với Spring; `UPLOAD_ROOT` trỏ đúng `app.storage.upload-dir`.
- `data/` là dữ liệu của worker, không nằm trong DB: mất thư mục này thì tìm kiếm vẫn chạy (tiền xử lý lại từ `chunk_text`) nhưng **mất vị trí chi tiết** cho tới khi bóc tách lại PDF.
- Worker không có xác thực: chỉ bind `127.0.0.1`.
- Kho còn quá nhỏ (vài tài liệu) thì `min_df=3`/`max_df=0.5` có thể loại hết từ — worker tự fit lại không cắt tỉa và ghi cảnh báo.

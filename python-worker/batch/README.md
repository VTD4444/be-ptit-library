# Xử lý hàng loạt PDF (bóc tách trên Kaggle, nạp vào CSDL sau)

Dùng khi có nhiều PDF cần gắn vào các biểu ghi **đã có** mà không muốn tải lên từng file qua giao diện,
hoặc máy không đủ mạnh để bóc tách. Phần nặng (bóc tách, chia đoạn, sinh embedding) chạy trên Kaggle GPU và
**không kết nối CSDL**; kết quả là file, xem lại được rồi mới nạp vào CSDL từ máy của bạn.

Code bóc tách là của worker (`pdf_extract`, `chunker`, `copy_store`), nên kết quả giống hệt tải lên qua Spring:
`digital_copies`, `background_jobs`, `search_document_chunks` kèm embedding, PDF trong `uploads/`, dữ liệu vị trí
trong `python-worker/data/copies/`.

| Bước | Script | Chạy ở đâu | CSDL |
|---|---|---|---|
| 1. Ghép file ↔ biểu ghi | `match_pdfs` | Máy bạn | Chỉ đọc |
| 2. Đóng gói | `pack_for_kaggle` | Máy bạn | Chỉ đọc cấu hình |
| 3. Bóc tách + embedding | `kaggle_extract` | Kaggle GPU | **Không** |
| 4. Nạp kết quả | `import_results` | Máy bạn (có PDF gốc) | Ghi |

Id bản số và id đoạn được sinh cố định từ (biểu ghi, nội dung file), nên chạy lại bước 3 hay bước 4 đều
không tạo bản trùng.

## 1. Ghép PDF với biểu ghi

```powershell
cd python-worker
.venv\Scripts\python -m batch.match_pdfs "D:\Books" "D:\Hieu" --out mapping.csv
```

Nhận nhiều thư mục cùng lúc; tên các thư mục phải khác nhau. Cột `file` có dạng `<tên thư mục>/<đường dẫn>`.
Tên file kiểu `BG ... - 2016`, `... - Tác giả`, nhan đề có phụ đề sau `:` đều được xử lý.

Mở `mapping.csv` bằng Excel:

| status | Ý nghĩa | Việc cần làm |
|---|---|---|
| `AUTO` | Khớp chắc | Liếc qua cột `matched_title` |
| `REVIEW` | Gần giống, hoặc nhiều file cùng ghép vào một biểu ghi (cột `note`) | Sửa `document_id` cho đúng |
| `NONE` | Không tìm thấy | Tra id trong `documents.csv` rồi điền, hoặc để trống để bỏ qua |

Để trống `document_id` = bỏ qua file đó ở mọi bước sau. Id biểu ghi cũng xem được trên URL trang sửa:
`/staff/materials/edit/<document_id>`. Lưu lại dạng **CSV UTF-8**.

## 2. Đóng gói

```powershell
.venv\Scripts\python -m batch.pack_for_kaggle mapping.csv "D:\Books" "D:\Hieu" --out kaggle-upload.zip
```

Kiểm tra trước mọi file trong mapping (có tồn tại, đúng PDF, không quá 100 MB). Zip gồm `worker/` (code),
`pdfs/`, `mapping.csv`, `chunk_config.json` (cấu hình chia đoạn + model embedding đọc từ CSDL).
**Không** chứa `.env` hay mật khẩu.

## 3. Chạy trên Kaggle

1. kaggle.com → **Datasets → New Dataset** → tải `kaggle-upload.zip` lên, để **Private** (ví dụ tên `ptit-pdfs`).
2. **Code → New Notebook** → **Add Input** → chọn dataset vừa tạo.
3. **Session options**: *Accelerator* = **GPU T4 x2**, *Internet* = **On** (để tải model embedding; cần xác minh số điện thoại).
4. Dán và chạy các ô (đổi đường dẫn theo `!ls /kaggle/input`):

```python
# Ô 1: cài thư viện (giữ PyTorch GPU có sẵn của Kaggle)
!pip install -q -r /kaggle/input/ptit-pdfs/worker/requirements-kaggle.txt
```

```python
# Ô 2: chép code ra thư mục ghi được
import shutil
shutil.copytree("/kaggle/input/ptit-pdfs/worker", "/kaggle/working/worker", dirs_exist_ok=True)
%cd /kaggle/working/worker
```

```python
# Ô 3: chạy thử 3 file trước
!python -m batch.kaggle_extract --bundle /kaggle/input/ptit-pdfs --out /kaggle/working/output --device cuda --limit 3
```

```python
# Ô 4: chạy hết (file đã xong ở ô 3 được bỏ qua)
!python -m batch.kaggle_extract --bundle /kaggle/input/ptit-pdfs --out /kaggle/working/output --device cuda
```

```python
# Ô 5: nén kết quả
!cd /kaggle/working && zip -qr output.zip output && ls -lh output.zip
```

Với khối lượng lớn nên bấm **Save Version → Save & Run All (Commit)** để Kaggle chạy nền (tối đa 12 giờ), xong vào tab
**Output** tải `output.zip`. Bị ngắt thì chạy lại: file đã có trong `output/results/` được bỏ qua.

`output/report.csv` cho biết từng file: `OK`, `LOWQ` (ít chữ, thường là bản scan: chưa có OCR nên không tìm được
theo nội dung), `ERROR` (xem cột `note`).

## 4. Nạp vào CSDL (trên máy bạn)

Giải nén `output.zip` (được thư mục `output/` có `results/` và `copies/`), worker đang chạy:

```powershell
# Xem trước, không ghi gì
.venv\Scripts\python -m batch.import_results output "D:\Books" "D:\Hieu" --dry-run
# Nạp thật
.venv\Scripts\python -m batch.import_results output "D:\Books" "D:\Hieu"
```

- Truyền **đúng các thư mục PDF** như ở bước 1: script đối chiếu hash để chắc file không bị đổi sau khi bóc tách.
- PDF được chép vào thư mục lưu trữ của Spring (`UPLOAD_ROOT` trong `.env`, mặc định
  `be-ptit-library/uploads/digital-copies`; đổi bằng `--upload-dir`), dữ liệu vị trí vào `python-worker/data/copies/`.
- Mỗi file một giao dịch: file lỗi không ảnh hưởng file khác. Nạp lại thì file đã nạp được bỏ qua.
- Biểu ghi đã có bản số khác bị bỏ qua, trừ khi thêm `--replace`.
- Cuối cùng script gọi worker dựng lại chỉ mục TF-IDF (`POST /api/index/rebuild`).

Khi chuyển ứng dụng sang máy chủ khác, chép theo cả thư mục `uploads/digital-copies/docs/` và `python-worker/data/copies/`.

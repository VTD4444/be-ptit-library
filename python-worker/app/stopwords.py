# Tu dung (muc 1.3 buoc 5): tu xuat hien o hau het van ban nhung khong mang y nghia phan biet.
# Kho chu yeu la tieng Viet, da tach tu bang pyvi nen tu ghep viet dang "tuy_nhien".
# max_df (muc 1.4) se tiep tuc cat bo cac tu qua pho bien ma danh sach nay bo sot.

VIETNAMESE = {
    # Tu don: chi hu tu ngu phap. Khong dua am tiet hay dung dau tu ghep ("chi" trong "chi muc", "tu" trong
    # "tu dien", "khong" trong "khong gian", "dieu" trong "dieu khien"...) vi pyvi khong phai luc nao cung gop
    # duoc tu ghep, bo di se lam vo tu. Tu pho bien con lai de max_df cat.
    "và", "là", "của", "có", "các", "những", "được", "với", "một", "này", "đó", "kia", "ấy", "thì", "mà",
    "bị", "do", "vì", "nên", "nếu", "rằng", "nhưng", "cũng", "hoặc", "hay", "nào", "gì", "ở", "khi", "đã",
    "đang", "sẽ", "rất", "đều", "vẫn", "chưa", "rồi", "nó", "họ", "tuy", "dù", "cho", "để", "trong", "như",
    "à", "ạ", "ừ", "nhé", "thôi", "chứ",
    # Tu ghep (dang da tach tu)
    "tuy_nhiên", "bởi_vì", "vì_vậy", "vì_thế", "do_đó", "do_vậy", "cho_nên", "nhưng_mà", "trong_khi",
    "mặc_dù", "ngoài_ra", "chúng_ta", "chúng_tôi", "các_bạn", "như_vậy", "như_thế", "tất_cả", "một_số",
    "điều_này", "việc_này", "có_thể", "theo_đó", "sau_đó", "trước_đó", "khi_đó", "lúc_đó", "bên_cạnh",
    "đối_với", "thông_qua", "trong_đó", "từ_đó", "nghĩa_là", "tức_là", "ví_dụ", "chẳng_hạn", "hơn_nữa",
    "cũng_như", "nhằm", "để_mà", "sao_cho", "thực_sự", "hầu_hết", "đặc_biệt", "những_gì", "bao_gồm",
}

# Tu tieng Anh xen lan trong tai lieu chuyen nganh
ENGLISH = {
    "a", "an", "the", "and", "or", "but", "if", "then", "else", "of", "to", "in", "on", "at", "by", "for",
    "with", "about", "into", "through", "from", "up", "down", "out", "over", "under", "again", "here",
    "there", "when", "where", "why", "how", "all", "any", "both", "each", "more", "most", "other", "some",
    "such", "no", "not", "only", "so", "than", "too", "very", "can", "will", "just", "should", "is", "are",
    "was", "were", "be", "been", "have", "has", "had", "do", "does", "did", "i", "we", "you", "he", "she",
    "it", "its", "they", "this", "that", "these", "those", "as", "also", "may", "must",
}

STOPWORDS = frozenset(VIETNAMESE | ENGLISH)

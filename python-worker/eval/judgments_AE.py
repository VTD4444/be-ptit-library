"""
Nhan lien quan nhom A-E (Claude cham, can nguoi kiem tra lai). Chi so = vi tri trong pool (thu tu gop
tfidf -> vector -> hybrid, giong eval/out/pool_AE.txt); tai lieu trong pool khong liet ke = 0.
Thang: 2 = noi dung chinh ve chu de / tra loi truc tiep, 1 = co lien quan / nhac den dung doi tuong, 0 = khong.
"""
GRADES = {
    "A01": {0: 2, 1: 1, 2: 1, 3: 1, 5: 1},
    "A02": {0: 2},
    "A03": {0: 1, 1: 1, 2: 2, 3: 2, 4: 1, 5: 1, 6: 2, 7: 2, 8: 2, 9: 1},
    "A04": {},
    "A05": {0: 2, 1: 2, 2: 1},
    "A06": {0: 2},
    "A07": {0: 2},
    "A08": {0: 1},  # Bi Mat Phia Sau Dien Toan Dam May: "chia se kinh nghiem voi Howard Schultz, CEO Starbucks"
    "B01": {0: 1, 1: 2, 2: 1, 3: 1, 5: 1, 7: 1},
    "B02": {0: 2, 1: 2, 2: 1, 3: 1, 4: 1},
    "B03": {1: 1, 2: 1, 3: 2, 7: 1, 8: 1},
    "B04": {0: 2, 1: 2, 3: 2, 6: 1, 7: 2, 8: 1, 10: 1, 11: 1},
    "B05": {0: 2, 1: 2, 2: 2, 3: 1, 4: 2, 5: 1, 6: 1, 7: 1},
    "B06": {0: 2, 1: 2, 2: 1, 4: 1},
    "B07": {0: 1, 1: 2, 2: 1, 4: 1, 5: 1, 7: 1},
    "B08": {0: 1, 1: 2, 2: 2},
    "C01": {0: 2, 1: 1, 2: 1, 3: 2, 4: 1, 5: 1, 6: 1, 7: 1, 9: 2, 10: 1, 12: 1, 13: 1},
    "C02": {0: 2, 4: 1, 5: 1, 6: 2, 15: 1, 18: 1},
    "C03": {0: 1, 1: 2, 2: 2, 3: 2, 5: 1, 6: 1, 7: 1, 12: 1},
    "C04": {0: 2, 1: 1, 3: 1, 4: 2, 5: 1, 7: 1, 8: 1, 9: 1, 11: 1},
    "C05": {0: 2, 1: 1, 2: 1, 11: 1},
    "C06": {0: 1, 2: 2, 9: 1, 10: 1},
    "C07": {2: 2, 8: 1},
    "C08": {4: 2},
    "D01": {0: 2, 1: 1, 2: 1, 3: 1, 5: 1, 6: 2, 9: 1},
    "D02": {0: 2, 1: 2, 3: 1, 4: 2, 6: 1},
    "D03": {2: 1, 5: 2},
    "D04": {3: 2},
    "D05": {0: 2},
    "D06": {1: 2},
    "D07": {0: 2, 1: 1, 11: 1, 12: 2},
    "D08": {0: 2, 1: 1, 2: 2, 3: 1, 4: 1, 5: 1, 6: 2, 7: 1, 8: 1},
    "E01": {0: 2, 1: 2, 2: 1, 3: 1, 5: 1, 8: 1, 12: 1},
    "E02": {0: 1, 2: 1, 3: 1, 6: 1, 11: 2},
    "E03": {0: 2, 3: 1},
    "E04": {0: 2, 1: 1, 2: 1, 3: 1, 5: 1, 8: 2, 10: 2},
    "E05": {0: 1, 2: 1, 3: 1, 4: 2, 10: 2, 11: 2, 13: 1},
    "E06": {0: 2, 2: 1, 4: 1, 6: 1, 13: 1},
    "E07": {0: 1, 1: 1, 3: 2, 6: 1, 8: 1, 10: 1},
    "E08": {1: 2, 2: 2, 3: 1, 4: 2, 8: 2, 9: 2, 11: 1, 12: 1},
}

# Tai lieu lien quan nam NGOAI pool (khong che do nao dua vao top-10), biet duoc tu doan trich o truy van khac
# hoac tu ten tai lieu. Doc id day du lay tu titles.json theo tien to.
OUTSIDE_POOL = {
    "A04": {"f9cc9900": 1},  # Giao trinh Co so ATTT: "Whitman Diffie va Martin Hellman" (xem pool B01)
    "D04": {"3a668419": 2},  # Giao trinh Ly thuyet Tai chinh Tien te: Chuong VIII Lam phat va chinh sach tien te
    "E02": {"cd1e4382": 2},  # Ky nguyen tien dien tu: Bitcoin (sach chu de chinh, chu boc tach bi loi font)
}

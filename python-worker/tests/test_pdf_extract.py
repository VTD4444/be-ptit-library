from app.pdf_extract import _clean, _has_real_space, _is_phantom_space


def test_clean_dotless_i_with_acute_becomes_i():
    # LaTeX: "phân tích" duoc ghi thanh "t" + "ı" (U+0131) + dau sac rieng
    assert _clean("phân tı́ch") == "phân tích"


def test_clean_rejoins_mark_after_space():
    assert _clean("Cài đă ̣t") == "Cài đặt"


def test_space_overlapping_next_char_is_phantom():
    # Toa do lay tu PDF that: dau cach [533.9, 536.0], chu "t" bat dau tai 533.9
    assert _is_phantom_space((533.9, 0, 536.0, 10), (528.0, 0, 533.9, 10), (533.9, 0, 537.2, 10), 12.0)


def test_space_inside_previous_glyph_is_phantom():
    # "ơ" [247.0, 253.4], dau cach [251.6, 253.5] nam de len chinh chu "ơ"
    assert _is_phantom_space((251.6, 0, 253.5, 10), (247.0, 0, 253.4, 10), (254.0, 0, 260.0, 10), 12.0)


def test_real_space_is_kept():
    assert not _is_phantom_space((165.4, 0, 168.4, 10), (159.4, 0, 165.4, 10), (168.4, 0, 174.0, 10), 12.0)


def _ch(c, x0, x1):
    return {"c": c, "bbox": (x0, 0, x1, 10)}, 12.8


def test_line_with_wide_space_has_real_space():
    # Toa do that tu "Sổ Tay Luật Sư": "trấ n" (dau cach rong 0) roi " nơ" (dau cach that)
    line = [_ch("ấ", 80.71, 86.24), _ch(" ", 86.33, 89.44), _ch("n", 86.33, 92.56),
            _ch(" ", 92.44, 95.55), _ch("n", 95.68, 101.91)]
    assert _has_real_space(line)


def test_line_with_only_zero_width_spaces_has_no_real_space():
    line = [_ch("ấ", 80.71, 86.24), _ch(" ", 86.33, 89.44), _ch("n", 86.33, 92.56)]
    assert not _has_real_space(line)

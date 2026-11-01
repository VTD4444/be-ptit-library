from psycopg_pool import ConnectionPool

from app import config

# prepare_threshold=None: tranh loi prepared statement khi di qua pgbouncer/Supabase pooler.
# check: kiem tra ket noi truoc khi giao ra - pooler/DB co the da cat ket noi nhan roi (restart, idle timeout),
#        neu khong job dau tien sau do se nhan ket noi chet va that bai.
# max_idle: chu dong dong ket noi nhan roi qua lau truoc khi phia server cat.
pool = ConnectionPool(
    config.DATABASE_URL,
    min_size=1,
    max_size=4,
    kwargs={"prepare_threshold": None},
    check=ConnectionPool.check_connection,
    max_idle=300,
    open=False,
)

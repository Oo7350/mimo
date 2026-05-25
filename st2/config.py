# 项目配置文件
import os

# 项目根路径
PATH = os.path.dirname(__file__)

# 测试环境地址（Mimo 本地前端）
BASE_URL = "http://localhost:5173"

# 默认测试账号
DEFAULT_USERNAME = "admin"
DEFAULT_PASSWORD = "123456"

# 浏览器设置
HEADLESS = False       # 是否无头模式（Jenkins 上设 True）
SLOW_MO = 0            # 操作间隔ms，调试时可设为 200
TIMEOUT = 10000        # 默认超时ms
VIEWPORT_WIDTH = 1920
VIEWPORT_HEIGHT = 1080

# 截图路径
IMG_DIR = os.path.join(PATH, "img")

# Allure 报告路径
REPORT_DIR = os.path.join(PATH, "report")
ALLURE_RESULTS = os.path.join(PATH, "allure-results")

# 日志路径
LOG_DIR = os.path.join(PATH, "log")

# ==================== API 配置 ====================
API_BASE_URL = "http://localhost:8080"

# 测试账号（与数据库种子数据一致）
TEST_ACCOUNTS = {
    "admin": {"username": "admin", "password": "123456", "role": "ROLE_ADMIN", "id": 1},
    "zhangsan": {"username": "zhangsan", "password": "123456", "role": "ROLE_MEMBER", "id": 2},
    "lisi": {"username": "lisi", "password": "123456", "role": "ROLE_MEMBER", "id": 3},
}

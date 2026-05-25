# 项目配置相关的文件
import os
from faker import Faker
# 获取项目路径[不同操作系统都可以获取]
PATH = os.path.dirname(__file__)
# print(PATH)
# 项目的地址[切换测试环境]
BASE_URL = "http://localhost:5173"
# 人的信息
fk = Faker(locale="zh_CN")
# 姓名，手机号，身份证
NAME = fk.name()
PHONE = fk.phone_number()
CARD = fk.ssn()
EMAIL=fk.email()
USER="18824797552"
USER_PASSWORD="Aa123456"

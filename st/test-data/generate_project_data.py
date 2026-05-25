import pymysql
import random
import string
from datetime import datetime

def generate_random_string(length=10):
    """生成随机字符串"""
    letters = string.ascii_letters + string.digits
    return ''.join(random.choice(letters) for i in range(length))

# 创建连接
conn = pymysql.connect(host='localhost',
                       port=3306, user='root',
                       password='336699', db='mimo', charset='utf8')

# 获取游标
cursor = conn.cursor()
try:
    # 执行SQL
    sql = '''
    INSERT INTO `mimo`.`projects` (`id`, `name`, `key`, `description`, `template`, `team_id`, `owner_id`, `created_at`, `updated_at`, `deleted`) VALUES (%s, %s, %s, NULL, 'SCRUM', '1', '1', %s, %s, '0');
    '''
    
    project_start = 12
    for i in range(10000):
        current_id = project_start + i
        project_name = '测试项目{}'.format(current_id)
        # 使用简短的唯一key，避免超出字段长度
        project_key = 'K{}'.format(current_id)
        created_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        updated_at = created_at
        
        cursor.execute(sql, (current_id, project_name, project_key, created_at, updated_at))
        
        # 每1000条记录显示一次进度
        if (i + 1) % 1000 == 0:
            print("已插入 {} 条记录...".format(i + 1))
    
    conn.commit()
    print("数据插入完成！共插入 10000 条记录。")
except Exception as e:
    print(e)
    conn.rollback()

# 关闭游标
cursor.close()
# 关闭连接
conn.close()
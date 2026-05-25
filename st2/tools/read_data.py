"""读取 JSON 测试数据 —— 与 Selenium 版一致"""
import json
from config import PATH


def read_json(file_name: str) -> list:
    """读取 data/ 下的 JSON 文件，返回列表"""
    file_path = PATH + "/data/" + file_name
    with open(file_path, mode='r', encoding='utf-8') as f:
        return json.load(f)

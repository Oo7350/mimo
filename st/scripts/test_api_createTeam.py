# 导包
import pytest
from api.mimo_api import MimoAPI
from tools import *


# 创建测试类
class TestCreateTeam:
    # 初始化属性
    token = None

    # 前置处理
    def setup_class(self):
        # 初始化Mimo API接口类
        self.mimo_api = MimoAPI()
        # 先登录获取token
        login_response = self.mimo_api.login({"username": "admin", "password": "123456"})
        assert login_response.status_code == 200
        TestCreateTeam.token = login_response.json()['data']['token']

    # 创建团队测试
    @pytest.mark.parametrize("name,description,status,errno,errmsg", read_json("create_team.json"))
    def test_create_team(self, name, description, status, errno, errmsg):
        data = {
            "name": name,
            "description": description
        }
        response = self.mimo_api.create_team(data)
        # 断言
        print(response.json())
        assert response.status_code == status
        assert response.json()['code'] == errno
        if errno == 200:
            assert "data" in response.json()
            print(f"创建团队成功，团队ID: {response.json()['data']['id']}")
        else:
            assert errmsg in str(response.json())

    # 获取我的团队列表
    def test_get_my_teams(self):
        response = self.mimo_api.get_my_teams()
        # 断言
        print(response.json())
        assert response.status_code == 200
        assert response.json()['code'] == 200
        assert isinstance(response.json()['data'], list)
        print(f"我的团队列表: {len(response.json()['data'])} 个团队")
        for team in response.json()['data']:
            print(f"  - {team['name']} (ID: {team['id']})")

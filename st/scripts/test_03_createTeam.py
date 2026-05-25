from pages.page_createTeam import CreateTeam


class TestCreateTeam:
    def test_01_create_team(self,web_driver, login):
        """测试创建团队"""
        team = CreateTeam(driver=web_driver)
        team.create_team("测试团队11111","测试团队描述")
        assert '成功' in team.get_result()

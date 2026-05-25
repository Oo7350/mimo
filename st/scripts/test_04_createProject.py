from pages.page_createProject import CreateProject


class TestCreateProject:

    def test_01_create_project(self,web_driver, login):
        """测试创建项目"""
        project = CreateProject(driver=web_driver)
        project.create_project("测试项目11211",'11112Key')
        assert '成功' in project.get_result()

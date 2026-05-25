from playwright.sync_api import sync_playwright

def test_login():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        page = browser. new_page()
        page.goto("http://localhost:5173")
        page.fill('xpath=/html/body/div[1]/div/div/form/div[1]/div/div/div/input',"admin")
        page.fill('xpath=/html/body/div[1]/div/div/form/div[2]/div/div/div/input',"123456")
        page.click('xpath=/html/body/div[1]/div/div/form/div[3]/div/button')
        page.wait_for_timeout(2000)
        page.screenshot(path="after_login.png")
        page.wait_for_selector('//*[@id="app"]/section/section/main/div/div[1]/h2',timeout=5000)
        print("点击成功!")
        browser.close()

if __name__ == '__main__':
    test_login()
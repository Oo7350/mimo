from flask import Flask, request, jsonify, render_template_string

app = Flask(__name__)

# 模拟用户数据库
USER_DB = {
    "admin": {"password": "abc123", "email": "admin@test.com"},
    "testuser1": {"password": "pass456", "email": "user1@test.com"},
}

# 模拟注册数据池
REGISTERED_USERS = set()

# --- 前端 HTML 模板（嵌入式） ---
INDEX_HTML = """
<!DOCTYPE html>
<html>
<head>
    <title>WebLoginValidator - 压力测试模拟系统</title>
    <style>
        body { font-family: sans-serif; padding: 2rem; max-width: 600px; margin: auto; }
        .box { border: 1px solid #ddd; padding: 20px; margin: 20px 0; border-radius: 5px; }
        input { display: block; width: 100%; padding: 8px; margin: 5px 0 10px; }
        button { padding: 10px 20px; background: #007bff; color: white; border: none; cursor: pointer; }
        button:hover { background: #0056b3; }
        #result { margin-top: 20px; padding: 10px; border: 1px solid #ccc; background: #f9f9f9; }
    </style>
</head>
<body>
    <h1>WebLoginValidator - 模拟登录系统</h1>
    <div class="box">
        <h2>登录</h2>
        <form id="loginForm">
            <label>用户名:</label>
            <input type="text" id="username" value="admin" />
            <label>密码:</label>
            <input type="password" id="password" value="abc123" />
            <button type="submit">登录</button>
        </form>
    </div>

    <div class="box">
        <h2>注册</h2>
        <form id="registerForm">
            <label>用户名:</label>
            <input type="text" id="regUsername" />
            <label>密码:</label>
            <input type="password" id="regPassword" />
            <label>邮箱:</label>
            <input type="email" id="regEmail" />
            <button type="submit">注册</button>
        </form>
    </div>

    <div class="box">
        <h2>找回密码</h2>
        <form id="forgotForm">
            <label>邮箱:</label>
            <input type="email" id="forgotEmail" />
            <button type="submit">发送重置邮件</button>
        </form>
    </div>

    <div id="result"></div>

    <script>
        // 通用 AJAX 请求函数
        async function submitForm(event, url, formData) {
            event.preventDefault();
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams(formData)
            });
            const result = await response.json();
            document.getElementById('result').innerHTML = `<strong>结果:</strong> ${JSON.stringify(result, null, 2)}`;
        }

        document.getElementById('loginForm').addEventListener('submit', function(e) {
            submitForm(e, '/login', { 
                username: document.getElementById('username').value, 
                password: document.getElementById('password').value 
            });
        });

        document.getElementById('registerForm').addEventListener('submit', function(e) {
            submitForm(e, '/register', {
                username: document.getElementById('regUsername').value,
                password: document.getElementById('regPassword').value,
                email: document.getElementById('regEmail').value
            });
        });

        document.getElementById('forgotForm').addEventListener('submit', function(e) {
            submitForm(e, '/forgot', {
                email: document.getElementById('forgotEmail').value
            });
        });
    </script>
</body>
</html>
"""

# 根路径：返回前端页面，避免 404
@app.route('/')
def index():
    return render_template_string(INDEX_HTML)

# 登录接口
@app.route('/login', methods=['POST'])
def login():
    username = request.form.get('username')
    password = request.form.get('password')

    if not username:
        return jsonify({"code": 400, "message": "用户名不能为空"}), 400
    if not password:
        return jsonify({"code": 400, "message": "密码不能为空"}), 400

    user = USER_DB.get(username)
    if user and user['password'] == password:
        return jsonify({"code": 200, "message": "登录成功", "username": username}), 200
    else:
        return jsonify({"code": 401, "message": "用户名或密码错误"}), 401

# 注册接口
@app.route('/register', methods=['POST'])
def register():
    username = request.form.get('username')
    password = request.form.get('password')
    email = request.form.get('email')

    if not username or not password or not email:
        return jsonify({"code": 400, "message": "参数不完整"}), 400

    if username in USER_DB or username in REGISTERED_USERS:
        return jsonify({"code": 409, "message": "用户名已存在"}), 409

    REGISTERED_USERS.add(username)
    USER_DB[username] = {"password": password, "email": email}
    return jsonify({"code": 201, "message": "注册成功"}), 201

# 找回密码接口
@app.route('/forgot', methods=['POST'])
def forgot():
    email = request.form.get('email')

    if not email:
        return jsonify({"code": 400, "message": "邮箱不能为空"}), 400

    for user_data in USER_DB.values():
        if user_data['email'] == email:
            return jsonify({"code": 200, "message": "已发送重置邮件"}), 200

    return jsonify({"code": 404, "message": "邮箱未注册"}), 404

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
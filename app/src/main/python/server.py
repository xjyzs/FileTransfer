import flask
import os
import threading
import requests
from datetime import datetime
from waitress import serve

loc = "/sdcard/"

htmlHead = '''<!DOCTYPE html>
<meta charset="UTF-8">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" type="image/svg+xml"
          href='data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22256%22 height=%22256%22 viewBox=%220 0 256 256%22%3E%3Cpath d=%22M207,48c11.35.07,29.02-1.54,39.44.06,2.88.44,7.33,2.72,7.59,5.94l.98-1h1v183c-2.49,3.85-4.54,7.16-9.56,7.94-8.44,1.3-22.26-.02-31.44.06.22-2.52,1.18-4.93,1.1-7.52-.06-2.09-1.1-4.38-1.1-4.98v-18c0-1.58-2.06-5.79-1-8.51h-2c-.91-17.32-8.59-28.38-25.94-32.55l-116.32-.2c-10.52,2.56-19.51,9.22-23.5,19.5-5.55,14.27-.5,36.71-2.24,52.26-10-.08-25.24,1.35-34.44-.06-4.75-.73-6.53-3.91-9.56-6.94v-103c7.49-1.94,14.85-6.94,21.67-10.83,40.61-23.14,81.13-46.71,121.04-70.96l62.62-.17,1.67-4.04h0Z%22 fill=%22%23fecb3d%22/%3E%3Cpath d=%22M207,48l-1.67,4.04-62.62.17c-39.91,24.25-80.43,47.82-121.04,70.96-6.82,3.89-14.18,8.89-21.67,10.83v-67c2.46-2.72,4.54-6.36,8.56-6.94,32.03-3.54,70.09,9.29,97-12,33.81.06,67.65-.26,101.45-.05h0Z%22 fill=%22%23fed45b%22/%3E%3Cpath d=%22M255,53l-.98,1c-.25-3.22-4.71-5.49-7.59-5.94-10.41-1.61-28.09,0-39.44-.06-33.8-.21-67.64.11-101.45.05-26.9,21.3-64.97,8.46-97,12-4.02.58-6.09,4.22-8.56,6.94V28c2.5-3.95,4.52-7.03,9.56-7.94,21.29,1.57,46.08-2.32,66.94-.07,14.67,1.58,19.34,15.71,28.99,24.01l140.03-.03c4.71.61,9.71,3.63,9.48,9.02h.02Z%22 fill=%22%23de9e01%22/%3E%3Cpath d=%22M212,205c.68,12.88-.5,26.09,0,39-55.94.42-112.06.43-168,0,1.74-15.55-3.31-37.99,2.24-52.26,3.99-10.28,12.98-16.94,23.5-19.5l116.32.2c17.35,4.17,25.03,15.22,25.94,32.56ZM77,204c-1.36,1.94-1.05,8,1.5,8h99c.73,0,3.08-3.17,2.55-4.45.06-1.04-2.05-3.55-2.55-3.55h-100.5Z%22 fill=%22%230b7cca%22/%3E%3Cpath d=%22M212,205h2c-1.06,2.71,1,6.92,1,8.5v18c0,.61,1.04,2.9,1.1,4.98.08,2.59-.89,4.99-1.1,7.52h-3c-.5-12.91.68-26.12,0-39Z%22 fill=%22%23de9e01%22/%3E%3Cpath d=%22M77,204h100.5c.5,0,2.61,2.51,2.55,3.55.53,1.28-1.82,4.45-2.55,4.45h-99c-2.55,0-2.86-6.06-1.5-8Z%22 fill=%22%23114a8b%22/%3E%3C/svg%3E'>
    <style>html::before {
        content: "";
        width: 100%;
        height: 100%;
        position: fixed;
        z-index: -1;
        background-image: linear-gradient(120deg, #e0fffc 0%, #f0ffdf 100%);
    }

    body {
        background-color: #F1FCF3;
    }

    a {
        color: #597A6C;
        font-size: 24px;
        text-decoration: none;
        word-break: break-all;
        margin: 10px
    }

    a:hover {
        color: #2B3B34;
    }

    p {
        font-size: 24px;
        word-break: break-all;
        margin: 10px;
    }

    #topBar {
        position: sticky;
        top: 6px;
        z-index: 1000;
    }

    .pth {
        background-color: #CACACA4C;
        backdrop-filter: blur(4px);
        transition: transform 0.3s, background-color 0.5s;
    }


    .circle {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        user-select: none;
    }

    .circle:hover,#pth1:hover {
        background-color: #9696964C;
        transform: translateY(-1px);
    }

    #pth1:hover{
        transform: translateY(-1px)scale(1.003);
    }

    #pth1 {
        border-radius: 50px;
        padding-left: 15px;
        padding-right: 15px;
        flex: 1;
        overflow: hidden;
        direction: rtl;
        text-align: left;
    }

    #grid span {
        color: #4C4C4C99;
        font-size: 24px;
        margin: 10px;
    }

    .btn, input[type="file"]::file-selector-button {
        background-color: #6464644C;
        border: none;
        border-radius: 8px;
        backdrop-filter: blur(4px);
    }</style>
</head><title>'''.replace('    ', '').replace('\n', '')
html = '''</title>
<p style="font-size: 42px;">File Explorer</p>
<div id="topBar">
    <div style="display: flex;gap: 10px;">
        <div class="pth circle" id="back"><p style="font-size:16px;color:black">↑</p></div>
        <div class="pth" id="pth1"><span
                style="white-space: nowrap;direction: ltr;display: inline-block; font-size: 20px;"
                id="pthContent"></span></div>
        <div class="pth circle"><a href="/nav" style="font-size:16px;color:black">⋯</a></div>
    </div>
    <form id="uploadForm" action="." method="post" enctype="multipart/form-data">
        <div style="display: flex;justify-content: flex-end;padding-top: 6px">
            <div style="display: flex;flex-direction: column;">
                <div style="display: flex; gap: 10px;">
                    <input type="file" id="file" name="file" required>
                    <button type="submit" class="btn">上传</button>
                </div>
                <div id="progressContainer" style="width: 100%;display:none;">
                    <div style="display: flex;justify-content: space-between">
                        <span id="speedText" style="font-size: 14px;"></span>
                        <span id="progressText" style="font-size: 14px"></span>
                    </div>
                    <div id="progressBar"
                         style="width:0; height:2px; background: #6464644C;border-radius: 1px"></div>
                </div>
            </div>
        </div>
    </form>
</div>
<div id="grid" style="display: grid;grid-template-columns: 1fr 0.5fr;line-height: 24px;"></div>
<script>
    function numToSize(n) {
        if (n < 1024) {
            return n + "B";
        } else if (n < 1048576) {
            return (n / 1024).toFixed(2) + "KB";
        } else if (n < 1073741824) {
            return (n / 1048576).toFixed(2) + "MB";
        } else {
            return (n / 1073741824).toFixed(2) + "GB";
        }
    }

    async function send() {
        document.getElementById("pthContent").textContent = decodeURIComponent(window.location.pathname).replace('files/', '')
        const res = await fetch(window.location, {method: 'OPTIONS'});
        const resClone = res.clone();
        const files = await res.json().catch(async () => {
        const text = await resClone.text();
        alert(text);return;
        });
        const grid = document.getElementById('grid');
        grid.innerHTML = "";
        for (const [name, size] of files) {
            const isDir = size === -1;
            let displaySize = ''
            if (!isDir) {
                displaySize = numToSize(size);
            }
            const a = document.createElement("a");
            a.textContent = name;
            a.href = name;
            if (isDir) {
                a.addEventListener("click", (e) => {
                    e.preventDefault();
                    const newUrl = name + "/";
                    history.pushState({}, "", newUrl);
                    send();
                });
            }
            const span = document.createElement("span");
            span.textContent = isDir ? "-" : displaySize;
            grid.appendChild(a);
            grid.appendChild(span);
        }
    }

    document.getElementById("back").addEventListener("click", () => {
        let path = location.pathname;
        if (!path.endsWith("/")) path += "/";
        const parts = path.split("/").filter(x => x.length > 0);
        parts.pop();
        const newPath = "/" + (parts.length ? parts.join("/") + "/" : "");
        history.pushState({}, "", newPath);
        send();
    });
    window.addEventListener("popstate", () => {
        send();
    });
    const form = document.getElementById('uploadForm');
    const fileInput = document.getElementById('file');
    const progressContainer = document.getElementById('progressContainer');
    const progressBar = document.getElementById('progressBar');
    const progressText = document.getElementById('progressText');
    const speedText = document.getElementById('speedText');
    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const file = fileInput.files[0];
        const formData = new FormData();
        formData.append('file', file);
        const xhr = new XMLHttpRequest();
        xhr.open('POST', form.action, true);
        let lastLoaded = 0;
        let lastTime = Date.now();
        xhr.upload.addEventListener('progress', function (e) {
            if (e.lengthComputable) {
                let currentTime = Date.now();
                const percent = (e.loaded / e.total * 100).toFixed(1);
                progressBar.style.width = percent + '%';
                progressText.textContent = percent + '%';
                speedText.textContent = numToSize((e.loaded - lastLoaded) / (currentTime - lastTime) * 1000) + '/s';
                lastTime = currentTime;
                lastLoaded = e.loaded;
                progressContainer.style.display = 'block';
            }
        });
        xhr.onload = function () {
            if (xhr.status === 200) {
                progressText.textContent = 'success';
                progressBar.style.width = '100%';
            } else {
                progressText.textContent = 'failure';
            }
            send();
        };

        xhr.send(formData);
    });
    document.addEventListener('DOMContentLoaded', function () {
        send();
    })</script>'''

app = flask.Flask(__name__)


@app.route('/', methods=['GET', 'POST', 'OPTIONS'])
@app.route('/<path:pth>', methods=['GET', 'POST', 'OPTIONS'])
def file(pth=''):
    if flask.request.method == 'GET':
        if f'{loc}{pth}'[-1] == '/':
            pth = pth[:-1]
        if os.path.isfile(f'{loc}{pth}'):
            try:
                return flask.send_file(f'{loc}{pth}', download_name=pth.split('/')[-1],
                                       as_attachment=pth.lower().endswith(('.py', '.bat')))
            except Exception as e:
                return str(e)
        return htmlHead + 'File Explorer' + html
    elif flask.request.method == 'POST':
        file = flask.request.files['file']
        filename = file.filename
        file.save(os.path.join(f'{loc}{pth}', filename))
        return flask.redirect(flask.request.url)
    else:
        final = [];
        directories = [];
        files = []
        try:
            for i in os.listdir(f'{loc}{pth}'):
                if os.path.isfile(f'{loc}{pth}/{i}'):
                    files.append(i)
                else:
                    directories.append(i)
            for i in sorted(directories):
                final.append([i, -1])
            for i in sorted(files):
                size = os.path.getsize(f'{loc}{pth}/{i}')
                final.append([i, size])
            return flask.jsonify(final)
        except Exception as e:
            return str(e)

@app.after_request
def log_request(response):
    now = datetime.now().strftime('%d/%b/%Y %H:%M:%S')
    if response.status_code != 200:
        log_info = f'{flask.request.remote_addr} - - [{now}] "\033[35m{flask.request.method} {flask.request.path} {flask.request.scheme.upper()}\033[0m" {response.status_code} -'
    else:
        log_info = f'{flask.request.remote_addr} - - [{now}] "{flask.request.method} {flask.request.path} {flask.request.scheme.upper()}" {response.status_code} -'
    print(log_info)

    return response

def upload(ip, port, url,dict):
    ipNew = ip
    if ':' in ip:
        ipNew = f'[{ip}]'
    try:
        requests.post(url, data={'loc': f"http://{ipNew}:{port}"})
        print(dict['uploadSuccessful'])
    except Exception as e:
        print(f"{dict['uploadFailed']}: {str(e)}")


def main(ip, port, url,dict):
    threading.Thread(target=upload, args=(ip, port, url, dict)).start()
    print(f"{dict['serverUrl']}: \033[33mhttp://{ip}:{port}/\033[0m")
    serve(app,host=ip, port=port,threads=8)
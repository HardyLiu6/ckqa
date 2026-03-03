#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MinerU PDF解析 - 简化版
========================
快速测试脚本，将本地PDF上传到MinerU云端解析并获取JSON结果

使用方法:
1. 修改下方的 TOKEN 和 PDF_FILE 变量
2. 运行: python mineru_simple.py
"""

import requests
import json
import time
import os
import zipfile
import io

# ===================== 配置区（请修改这里）=====================
TOKEN = "eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFM1MTIifQ.eyJqdGkiOiIzNTQwMDEzNCIsInJvbCI6IlJPTEVfUkVHSVNURVIiLCJpc3MiOiJPcGVuWExhYiIsImlhdCI6MTc2ODgyMjQ0OSwiY2xpZW50SWQiOiJsa3pkeDU3bnZ5MjJqa3BxOXgydyIsInBob25lIjoiIiwib3BlbklkIjpudWxsLCJ1dWlkIjoiYmEzNTZhODQtOWIyNi00ODUxLWJmMGMtY2M4YTQ4MjcyY2E4IiwiZW1haWwiOiIiLCJleHAiOjE3NzAwMzIwNDl9.jBI71Dq3JZ3YFJb8mJGRCfJUurREtzsXP9gq-rkV_orJrIGKwCdlKS_SUW_ci2LLqX1KD3nAwGjHjsD3BsQIXw"      # 替换为你的MinerU API Token
PDF_FILE = "data/os/book.pdf"          # 替换为你的PDF文件路径
OUTPUT_DIR = "artifacts/test/mineru/sim" # 输出目录
# ==============================================================

API_BASE = "https://mineru.net/api/v4"
HEADERS = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {TOKEN}"
}


def main():
    # 检查配置
    if TOKEN == "YOUR_API_TOKEN":
        print("❌ 请先修改脚本中的 TOKEN 变量!")
        print("   获取方式: https://mineru.net/apiManage")
        return
    
    if not os.path.exists(PDF_FILE):
        print(f"❌ 文件不存在: {PDF_FILE}")
        return
    
    file_name = os.path.basename(PDF_FILE)
    print(f"📄 开始解析: {file_name}")
    print(f"   大小: {os.path.getsize(PDF_FILE)/1024/1024:.2f} MB")
    
    # 1. 申请上传链接
    print("\n[1/4] 申请上传链接...")
    resp = requests.post(
        f"{API_BASE}/file-urls/batch",
        headers=HEADERS,
        json={
            "files": [{"name": file_name, "is_ocr": True, "data_id": "test"}],
            "model_version": "vlm",      # vlm=高精度, pipeline=快速
            "enable_formula": True,       # 公式识别
            "enable_table": True,         # 表格识别
            "language": "ch"              # ch=中文, en=英文
        }
    )
    
    result = resp.json()
    if result.get("code") != 0:
        print(f"❌ 失败: {result.get('msg')}")
        return
    
    batch_id = result["data"]["batch_id"]
    upload_url = result["data"]["file_urls"][0]
    print(f"   ✓ Batch ID: {batch_id}")
    
    # 2. 上传文件
    print("\n[2/4] 上传文件...")
    with open(PDF_FILE, 'rb') as f:
        resp = requests.put(upload_url, data=f)
    
    if resp.status_code != 200:
        print(f"❌ 上传失败: {resp.status_code}")
        return
    print("   ✓ 上传成功!")
    
    # 3. 等待解析完成
    print("\n[3/4] 等待解析...(可能需要几分钟)")
    for i in range(120):  # 最多等待10分钟
        resp = requests.get(
            f"{API_BASE}/extract-results/batch/{batch_id}",
            headers=HEADERS
        )
        result = resp.json()
        
        if result.get("code") != 0:
            print(f"❌ 查询失败: {result.get('msg')}")
            return
        
        file_result = result["data"]["extract_result"][0]
        state = file_result.get("state", "")
        
        if state == "done":
            print(f"   ✓ 解析完成!")
            break
        elif state == "failed":
            print(f"❌ 解析失败: {file_result.get('err_msg')}")
            return
        else:
            progress = file_result.get("extract_progress", {})
            pages = f"{progress.get('extracted_pages', '?')}/{progress.get('total_pages', '?')}"
            print(f"   ⏳ 进度: {pages} 页 ({i*5}秒)", end='\r')
            time.sleep(5)
    else:
        print("\n❌ 解析超时")
        return
    
    # 4. 下载结果
    print("\n[4/4] 下载结果...")
    zip_url = file_result.get("full_zip_url")
    if not zip_url:
        print("❌ 没有下载链接")
        return
    
    resp = requests.get(zip_url)
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # 解压并读取JSON
    with zipfile.ZipFile(io.BytesIO(resp.content)) as zf:
        zf.extractall(OUTPUT_DIR)
        
        # 找到JSON文件
        json_file = None
        for name in zf.namelist():
            if 'content_list.json' in name:
                json_file = name
                break
        
        print(f"\n{'='*50}")
        print(f"✅ 完成! 结果保存在: {OUTPUT_DIR}")
        print(f"{'='*50}")
        print("\n📦 解压的文件:")
        for name in zf.namelist():
            print(f"   - {name}")
        
        # 读取并显示JSON预览
        if json_file:
            json_path = os.path.join(OUTPUT_DIR, json_file)
            with open(json_path, 'r', encoding='utf-8') as f:
                content = json.load(f)
            
            print(f"\n📋 JSON内容预览 (共{len(content)}个元素):")
            print("-"*50)
            
            # 显示前3个元素
            for i, item in enumerate(content[:3]):
                item_type = item.get("type", "unknown")
                if item_type == "text":
                    text = item.get("text", "")[:100]
                    print(f"[{i}] 文本: {text}...")
                elif item_type == "image":
                    print(f"[{i}] 图片: {item.get('img_path', '')}")
                elif item_type == "table":
                    print(f"[{i}] 表格")
                else:
                    print(f"[{i}] {item_type}")
            
            if len(content) > 3:
                print(f"... 还有 {len(content)-3} 个元素")
            
            # 保存完整JSON路径
            print(f"\n💡 完整JSON文件: {json_path}")


if __name__ == "__main__":
    main()
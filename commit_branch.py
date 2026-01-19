#!/usr/bin/env python3
"""
将当前分支合并到main分支，并使用squash方式提交，commit message为分支名
"""
import subprocess
import sys
import os

def run_cmd(cmd, check=True):
    """执行shell命令"""
    print(f"执行: {cmd}")
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"错误: {result.stderr}")
        sys.exit(1)
    return result.stdout.strip()

def main():
    # 获取当前分支名
    current_branch = run_cmd("git branch --show-current")
    if not current_branch:
        print("错误: 无法获取当前分支名")
        sys.exit(1)
    
    print(f"当前分支: {current_branch}")
    
    # 如果已经在main分支，直接退出
    if current_branch == "main":
        print("当前已在 main 分支，无需操作")
        sys.exit(0)
    
    # 检查是否有未提交的更改
    status = run_cmd("git status --porcelain", check=False)
    if status:
        print("警告: 检测到未提交的更改，请先提交或暂存")
        response = input("是否继续? (y/n): ")
        if response.lower() != 'y':
            print("已取消")
            sys.exit(0)
    
    # 切换到main分支
    print("\n切换到main分支...")
    run_cmd("git checkout main")
    
    # 拉取最新代码
    print("\n拉取最新代码...")
    run_cmd("git pull")
    
    # 合并分支（使用squash）
    print(f"\n合并分支 {current_branch} 到 main (squash)...")
    result = subprocess.run(f"git merge --squash {current_branch}", shell=True, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"\n❌ 合并失败")
        if result.stderr:
            print(result.stderr)
        if result.stdout:
            print(result.stdout)
        print(f"\n请手动处理合并问题后，再执行以下命令完成提交:")
        print(f'  git commit -m "{current_branch}"')
        print(f"\n或者如果需要取消本次合并:")
        print(f"  git reset --hard HEAD")
        sys.exit(1)
    
    # 尝试提交，使用分支名作为commit message
    print(f"\n提交更改，commit message: {current_branch}")
    commit_result = subprocess.run(f'git commit -m "{current_branch}"', shell=True, capture_output=True, text=True)
    
    if commit_result.returncode != 0:
        print(f"\n❌ 提交失败，可能需要手动处理冲突或问题")
        if commit_result.stderr:
            print(commit_result.stderr)
        if commit_result.stdout:
            print(commit_result.stdout)
        print(f"\n当前状态: 已执行 squash 合并，但尚未提交")
        print(f"请手动解决冲突或问题后，执行以下命令完成提交:")
        print(f'  git commit -m "{current_branch}"')
        print(f"\n或者如果需要取消本次合并:")
        print(f"  git reset --hard HEAD")
        sys.exit(1)
    
    print(f"\n✅ 完成! 分支 {current_branch} 已成功合并到 main 分支")

if __name__ == "__main__":
    main()


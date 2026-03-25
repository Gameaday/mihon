import os
import re

# We skip build dirs, .git, etc.
SKIP_DIRS = {'.git', 'build', 'gradle', '.gradle', 'node_modules', '.idea'}

def should_skip(path):
    parts = path.split(os.sep)
    return any(p in SKIP_DIRS for p in parts)

def replace_content(content):
    # Rule 1: eu.kanade.domain -> ephyra.domain
    content = content.replace("eu.kanade.domain", "ephyra.domain")
    
    # Rule 2: eu.kanade.presentation -> ephyra.presentation
    content = content.replace("eu.kanade.presentation", "ephyra.presentation")
    
    # Rule 3: eu.kanade.tachiyomi -> ephyra.app (except .source and .network)
    content = re.sub(r'eu\.kanade\.tachiyomi(?!\.(source|network))', 'ephyra.app', content)
    
    # Rule 4: tachiyomi -> ephyra (when referring to package segment)
    content = re.sub(r'(^|[\s\(\<])tachiyomi\.', r'\1ephyra.', content)
    content = re.sub(r'\.tachiyomi\.', r'.ephyra.', content)
    content = re.sub(r'\.tachiyomi$', r'.ephyra', content)
    
    # Rule 5: mihon -> ephyra (to clean up any previous references to mihon)
    content = re.sub(r'(^|[\s\(\<])mihon\.', r'\1ephyra.', content)
    content = re.sub(r'\.mihon\.', r'.ephyra.', content)
    content = re.sub(r'\.mihon$', r'.ephyra', content)
    
    return content

def replace_path(file_path):
    # /eu/kanade/domain/ -> /ephyra/domain/
    new_path = file_path.replace("/eu/kanade/domain/", "/ephyra/domain/")
    
    # /eu/kanade/presentation/ -> /ephyra/presentation/
    new_path = new_path.replace("/eu/kanade/presentation/", "/ephyra/presentation/")
    
    # /eu/kanade/tachiyomi/ -> /ephyra/app/ (if not /source/ or /network/)
    if "/eu/kanade/tachiyomi/" in new_path:
        if not ("/eu/kanade/tachiyomi/source/" in new_path or "/eu/kanade/tachiyomi/network/" in new_path):
            new_path = new_path.replace("/eu/kanade/tachiyomi/", "/ephyra/app/")
            
    # /tachiyomi/ -> /ephyra/
    new_path = new_path.replace("/tachiyomi/", "/ephyra/")
    
    # /mihon/ -> /ephyra/
    new_path = new_path.replace("/mihon/", "/ephyra/")
    
    return new_path

moved_files = 0
modified_files = 0

for root, dirs, files in os.walk("."):
    if should_skip(root):
        continue
        
    for file in files:
        if not file.endswith(('.kt', '.java', '.xml', '.pro', '.kts')):
            continue
            
        file_path = os.path.join(root, file)
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
                
            new_content = replace_content(content)
            
            if new_content != content:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                modified_files += 1
                
            clean_path = file_path[2:] if file_path.startswith("./") else file_path
            new_clean_path = replace_path(clean_path)
            
            if clean_path != new_clean_path:
                new_full_path = os.path.join(".", new_clean_path)
                os.makedirs(os.path.dirname(new_full_path), exist_ok=True)
                os.rename(file_path, new_full_path)
                moved_files += 1
                
        except Exception as e:
            pass

print(f"Modified {modified_files} files.")
print(f"Moved {moved_files} files.")

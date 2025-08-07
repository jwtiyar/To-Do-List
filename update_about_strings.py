#!/usr/bin/env python3

import os
import re
import sys

# Standard English contact info to use across all languages
ENGLISH_CONTACT_INFO = [
    '    <string name="about_developer">Developed by: Jwtyar Nariman</string>',
    '    <string name="about_email">Email: jwtiyar@gmail.com</string>',
    '    <string name="about_github">GitHub: https://github.com/jwtiyar</string>'
]

def update_language_file(file_path):
    """Update a language file to remove about_summary and standardize contact info"""
    print(f"Processing: {file_path}")
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Remove about_summary line completely
        content = re.sub(r'    <string name="about_summary">.*?</string>\n', '', content, flags=re.DOTALL)
        
        # Find and replace the about contact info section
        # Pattern to match the entire about section
        about_pattern = r'(    <!-- About dialog strings -->.*?<string name="about_title">.*?</string>\n)(.*?)(    <!-- Theme selection strings -->|\n    <!-- Navigation drawer strings -->|\n    <!-- Toast messages -->|</resources>)'
        
        def replace_about_section(match):
            before = match.group(1)
            after = match.group(3)
            
            # Create new about section with English contact info
            new_contact_section = '\n'.join(ENGLISH_CONTACT_INFO) + '\n'
            
            return before + new_contact_section + '\n' + after
        
        # Apply the replacement
        content = re.sub(about_pattern, replace_about_section, content, flags=re.DOTALL)
        
        # Write back the updated content
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"  ✓ Updated: {file_path}")
        return True
        
    except Exception as e:
        print(f"  ✗ Error processing {file_path}: {e}")
        return False

def main():
    """Main function to update all language files"""
    base_path = "app/src/main/res"
    
    # List of language directories to process
    language_dirs = [
        "values-de", "values-es", "values-fr", "values-it", "values-ja", 
        "values-ko", "values-ku", "values-nl", "values-pt", "values-ru", 
        "values-tr", "values-zh"
    ]
    
    success_count = 0
    total_count = 0
    
    for lang_dir in language_dirs:
        file_path = os.path.join(base_path, lang_dir, "strings.xml")
        if os.path.exists(file_path):
            total_count += 1
            if update_language_file(file_path):
                success_count += 1
        else:
            print(f"Warning: File not found: {file_path}")
    
    print(f"\nCompleted: {success_count}/{total_count} files updated successfully")
    
    if success_count == total_count:
        print("✅ All language files updated successfully!")
        return 0
    else:
        print("⚠️ Some files had errors. Please check the output above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())

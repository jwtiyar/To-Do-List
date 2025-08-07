#!/usr/bin/env python3

import os
import re
import sys

def fix_language_file(file_path):
    """Fix a language file by only removing about_summary and updating contact info"""
    print(f"Processing: {file_path}")
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Remove only the about_summary line
        content = re.sub(r'    <string name="about_summary">.*?</string>\n', '', content, flags=re.DOTALL)
        
        # Update only the contact info strings in the about section
        content = re.sub(r'    <string name="about_developer">.*?</string>', 
                        '    <string name="about_developer">Developed by: Jwtyar Nariman</string>', 
                        content)
        content = re.sub(r'    <string name="about_email">.*?</string>', 
                        '    <string name="about_email">Email: jwtiyar@gmail.com</string>', 
                        content)
        content = re.sub(r'    <string name="about_github">.*?</string>', 
                        '    <string name="about_github">GitHub: https://github.com/jwtiyar</string>', 
                        content)
        
        # Write back the updated content
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"  ✓ Updated: {file_path}")
        return True
        
    except Exception as e:
        print(f"  ✗ Error processing {file_path}: {e}")
        return False

def add_missing_strings_to_file(file_path, missing_strings):
    """Add missing strings to a language file"""
    if not missing_strings:
        return True
        
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find where to insert the missing strings (before </resources>)
        insertion_point = content.rfind('</resources>')
        if insertion_point == -1:
            print(f"  ✗ Could not find </resources> tag in {file_path}")
            return False
        
        # Insert the missing strings
        new_strings = '\n'.join([f'    {s}' for s in missing_strings]) + '\n'
        content = content[:insertion_point] + new_strings + content[insertion_point:]
        
        # Write back the updated content
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"  ✓ Added {len(missing_strings)} missing strings to: {file_path}")
        return True
        
    except Exception as e:
        print(f"  ✗ Error adding strings to {file_path}: {e}")
        return False

def get_missing_button_strings():
    """Get the standard button strings that are missing"""
    return [
        '<string name="button_save">Save</string>',
        '<string name="button_ok">OK</string>', 
        '<string name="button_grant">Grant</string>',
        '<string name="button_open_settings">Open Settings</string>'
    ]

def main():
    """Main function to fix all language files"""
    base_path = "app/src/main/res"
    
    # Files that need button strings added (from lint report)
    files_needing_buttons = {
        "values-ru/strings.xml": get_missing_button_strings(),
        "values-pt/strings.xml": get_missing_button_strings(),
        "values-ku/strings.xml": get_missing_button_strings(),
        "values-nl/strings.xml": get_missing_button_strings(),
        "values-tr/strings.xml": get_missing_button_strings(),
    }
    
    success_count = 0
    total_count = 0
    
    for rel_file_path, missing_strings in files_needing_buttons.items():
        file_path = os.path.join(base_path, rel_file_path)
        if os.path.exists(file_path):
            total_count += 1
            if add_missing_strings_to_file(file_path, missing_strings):
                success_count += 1
        else:
            print(f"Warning: File not found: {file_path}")
    
    print(f"\nCompleted: {success_count}/{total_count} files updated successfully")
    
    if success_count == total_count:
        print("✅ All missing button strings added successfully!")
        return 0
    else:
        print("⚠️ Some files had errors. Please check the output above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())

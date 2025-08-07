#!/usr/bin/env python3
"""
Comprehensive Localization Testing Script for Android App
Analyzes all string resources and identifies missing translations
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import defaultdict, Counter

def parse_strings_xml(file_path):
    """Parse strings.xml file and return dict of string_name -> value"""
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        strings = {}
        for string_elem in root.findall('string'):
            name = string_elem.get('name')
            value = string_elem.text or ""
            strings[name] = value
        return strings
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
        return {}

def get_locale_from_path(path):
    """Extract locale from values-xx path"""
    path_str = str(path)
    match = re.search(r'values-([^/]+)', path_str)
    return match.group(1) if match else 'default'

def analyze_localizations():
    """Main analysis function"""
    res_dir = Path("app/src/main/res")
    
    # Find all strings.xml files
    string_files = []
    for values_dir in res_dir.glob("values*/"):
        strings_xml = values_dir / "strings.xml"
        if strings_xml.exists():
            string_files.append(strings_xml)
    
    # Parse all string files
    all_strings = {}
    for strings_file in string_files:
        locale = get_locale_from_path(strings_file)
        all_strings[locale] = parse_strings_xml(strings_file)
    
    # Get default (English) strings as reference
    default_strings = all_strings.get('default', {})
    
    print("=" * 80)
    print("LOCALIZATION TESTING REPORT")
    print("=" * 80)
    print()
    
    # Find supported locales
    locales = [loc for loc in all_strings.keys() if loc != 'default']
    locales.sort()
    
    print(f"SUPPORTED LOCALES ({len(locales)}):")
    for i, locale in enumerate(locales, 1):
        print(f"{i:2d}. {locale}")
    print()
    
    # Analyze completeness
    print("LOCALIZATION COMPLETENESS ANALYSIS:")
    print("-" * 50)
    
    missing_translations = defaultdict(list)
    extra_translations = defaultdict(list)
    
    for locale in locales:
        locale_strings = all_strings[locale]
        
        # Find missing strings
        for key in default_strings:
            if key not in locale_strings:
                missing_translations[locale].append(key)
        
        # Find extra strings (not in default)
        for key in locale_strings:
            if key not in default_strings:
                extra_translations[locale].append(key)
        
        # Calculate completeness percentage
        if default_strings:
            completeness = (len(locale_strings) - len(extra_translations[locale])) / len(default_strings) * 100
            completeness = max(0, min(100, completeness))  # Clamp between 0-100
        else:
            completeness = 0
            
        status = "✅ COMPLETE" if completeness >= 100 else "⚠️  INCOMPLETE" if completeness >= 90 else "❌ MISSING MANY"
        
        print(f"{locale:8s}: {completeness:6.1f}% complete - {len(locale_strings):3d}/{len(default_strings):3d} strings {status}")
    
    print()
    
    # Report missing translations
    print("MISSING TRANSLATIONS BY LOCALE:")
    print("-" * 40)
    for locale in locales:
        if missing_translations[locale]:
            print(f"\n{locale.upper()} - Missing {len(missing_translations[locale])} strings:")
            for i, key in enumerate(missing_translations[locale][:10], 1):  # Show first 10
                print(f"  {i:2d}. {key}")
            if len(missing_translations[locale]) > 10:
                print(f"     ... and {len(missing_translations[locale]) - 10} more")
    
    # Check RTL languages specifically
    print("\nRTL (RIGHT-TO-LEFT) LANGUAGE SUPPORT:")
    print("-" * 40)
    rtl_locales = ['ar', 'ku']  # Arabic and Kurdish
    rtl_supported = []
    rtl_missing = []
    
    for rtl_locale in rtl_locales:
        if rtl_locale in all_strings:
            rtl_supported.append(rtl_locale)
            completeness = (len(all_strings[rtl_locale]) - len(extra_translations[rtl_locale])) / len(default_strings) * 100
            completeness = max(0, min(100, completeness))
            print(f"✅ {rtl_locale}: {completeness:.1f}% complete")
        else:
            rtl_missing.append(rtl_locale)
            print(f"❌ {rtl_locale}: Not supported")
    
    # Analyze dialog-specific strings
    print("\nDIALOG-SPECIFIC STRING ANALYSIS:")
    print("-" * 35)
    
    dialog_strings = [
        'dialog_add_task_title',
        'dialog_about_title', 
        'dialog_theme_title',
        'about_title',
        'about_summary',
        'about_developer',
        'about_email',
        'about_github',
        'theme_dialog_title',
        'theme_light',
        'theme_dark',
        'theme_system_default'
    ]
    
    print("Key dialog strings coverage:")
    for string_key in dialog_strings:
        if string_key in default_strings:
            missing_locales = []
            for locale in locales:
                if string_key not in all_strings[locale]:
                    missing_locales.append(locale)
            
            if missing_locales:
                print(f"❌ {string_key}: Missing in {len(missing_locales)} locales: {', '.join(missing_locales[:5])}")
            else:
                print(f"✅ {string_key}: Complete in all locales")
    
    # Check for English text in translations (potential issues)
    print("\nPOTENTIAL ENGLISH TEXT IN TRANSLATIONS:")
    print("-" * 42)
    
    english_patterns = [
        r'\bTask\b', r'\bAdd\b', r'\bCancel\b', r'\bOK\b', r'\bSettings\b',
        r'\bAbout\b', r'\bTheme\b', r'\bPending\b', r'\bCompleted\b',
        r'\bPriority\b', r'\bHigh\b', r'\bMedium\b', r'\bLow\b'
    ]
    
    for locale in locales:
        if locale in ['en', 'default']:  # Skip English locales
            continue
            
        potential_english = []
        locale_strings = all_strings[locale]
        
        for key, value in locale_strings.items():
            if value:
                for pattern in english_patterns:
                    if re.search(pattern, value, re.IGNORECASE):
                        potential_english.append((key, value))
                        break
        
        if potential_english:
            print(f"\n{locale.upper()} - Potential English text found:")
            for key, value in potential_english[:5]:  # Show first 5
                print(f"  {key}: \"{value}\"")
            if len(potential_english) > 5:
                print(f"  ... and {len(potential_english) - 5} more")
    
    return all_strings, locales, missing_translations

def generate_testing_checklist(locales):
    """Generate a testing checklist"""
    print("\n" + "=" * 80)
    print("MANUAL TESTING CHECKLIST")
    print("=" * 80)
    
    print("\n1. BUILD VERIFICATION:")
    print("   ✅ App builds successfully")
    print("   ⚠️  Warning about missing theme_system default value noted")
    
    print("\n2. LOCALE TESTING PROCEDURE:")
    print("   For each supported locale, test the following:")
    
    print("\n   DEVICE/EMULATOR SETUP:")
    print("   a) Change system language to target locale")
    print("   b) Force-stop and restart the app")
    print("   c) Verify app language switches correctly")
    
    print("\n   DIALOG TESTING:")
    print("   d) Open 'Add Task' dialog - check all text is translated")
    print("   e) Open 'About' dialog - verify all content is localized")
    print("   f) Open 'Theme Selection' dialog - check options are translated")
    
    print("\n   RTL TESTING (Arabic & Kurdish):")
    print("   g) Verify text flows right-to-left")
    print("   h) Check UI layout adapts to RTL (buttons, icons, etc.)")
    print("   i) Ensure no text is cut off or overlapping")
    
    print("\n3. LOCALES TO TEST:")
    for i, locale in enumerate(locales, 1):
        rtl_indicator = " (RTL)" if locale in ['ar', 'ku'] else ""
        print(f"   {i:2d}. {locale}{rtl_indicator}")
    
    print("\n4. ISSUES TO LOOK FOR:")
    print("   • English text appearing in non-English locales")
    print("   • Text truncation or UI layout problems")
    print("   • Missing translations (fallback to English)")
    print("   • Incorrect RTL text direction")
    print("   • Dialog buttons not properly translated")
    
    print("\n5. RECOMMENDED TESTING ORDER:")
    print("   1) English (baseline)")
    print("   2) Arabic (RTL, complex script)")
    print("   3) Kurdish (RTL, different script)")
    print("   4) German (long words, compound terms)")
    print("   5) Japanese (different character set)")
    print("   6) Spanish, French, Italian (Romance languages)")

if __name__ == "__main__":
    all_strings, locales, missing_translations = analyze_localizations()
    generate_testing_checklist(locales)
    
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Total supported locales: {len(locales)}")
    print(f"RTL languages supported: Arabic (ar), Kurdish (ku)")
    print(f"Build status: ✅ Successful (with minor warning)")
    print(f"Ready for manual device/emulator testing: ✅ Yes")

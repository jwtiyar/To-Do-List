#!/usr/bin/env python3
"""
Script to add missing strings to incomplete locales
"""

import os

def add_missing_strings_to_locale(locale_code, locale_name, translations):
    """Add missing About and theme strings to a locale file"""
    file_path = f"app/src/main/res/values-{locale_code}/strings.xml"
    
    # Read current content
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Find the position to insert new strings (before </resources>)
    insert_pos = content.rfind('</resources>')
    if insert_pos == -1:
        print(f"Error: Could not find </resources> in {file_path}")
        return
    
    # Check if about_title already exists
    if 'about_title' in content:
        print(f"{locale_code}: Already has about_title, skipping")
        return
    
    # Prepare new strings to insert
    new_strings = f"""
    <!-- About dialog strings -->
    <string name="about_title">{translations['about_title']}</string>
    <string name="about_summary">{translations['about_summary']}</string>
    <string name="about_developer">{translations['about_developer']}</string>
    <string name="about_email">Email: jwtiyar@gmail.com</string>
    <string name="about_github">GitHub: https://github.com/jwtiyar</string>

    <!-- Theme selection strings -->
    <string name="theme_dialog_title">{translations['theme_dialog_title']}</string>
    <string name="theme_system_default">{translations['theme_system_default']}</string>

"""
    
    # Insert new content
    new_content = content[:insert_pos] + new_strings + content[insert_pos:]
    
    # Write back to file
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    
    print(f"✅ Added missing strings to {locale_code}")

# Define translations for each locale
locale_translations = {
    'es': {
        'about_title': 'Acerca de Tareas Simples',
        'about_summary': 'Tareas Simples te ayuda a organizar tus tareas diarias, establecer recordatorios y mantenerte productivo. Las funciones incluyen hermosos temas oscuro/claro, navegación intuitiva por pestañas y notificaciones persistentes para tus tareas importantes.',
        'about_developer': 'Desarrollado por: Jwtyar Nariman',
        'theme_dialog_title': 'Elegir Tema de la App',
        'theme_system_default': 'Predeterminado del Sistema'
    },
    'fr': {
        'about_title': 'À propos de Tâches Simples',
        'about_summary': 'Tâches Simples vous aide à organiser vos tâches quotidiennes, définir des rappels et rester productif. Les fonctionnalités incluent de beaux thèmes sombre/clair, une navigation intuitive par onglets et des notifications persistantes pour vos tâches importantes.',
        'about_developer': 'Développé par : Jwtyar Nariman',
        'theme_dialog_title': 'Choisir le thème de l\'app',
        'theme_system_default': 'Défaut du système'
    },
    'ko': {
        'about_title': '심플 태스크 정보',
        'about_summary': '심플 태스크는 일상 업무 정리, 미리알림 설정, 생산성 유지를 도와줍니다. 아름다운 다크/라이트 테마, 직관적인 탭 네비게이션, 중요한 작업에 대한 지속적인 알림 기능을 포함합니다.',
        'about_developer': '개발자: Jwtyar Nariman',
        'theme_dialog_title': '앱 테마 선택',
        'theme_system_default': '시스템 기본값'
    },
    'ru': {
        'about_title': 'О Простых Задачах',
        'about_summary': 'Простые Задачи помогают организовать ваши ежедневные задачи, устанавливать напоминания и оставаться продуктивным. Функции включают красивые тёмные/светлые темы, интуитивную навигацию по вкладкам и постоянные уведомления для важных задач.',
        'about_developer': 'Разработано: Jwtyar Nariman',
        'theme_dialog_title': 'Выбрать тему приложения',
        'theme_system_default': 'Системная по умолчанию'
    },
    'zh': {
        'about_title': '关于简单任务',
        'about_summary': '简单任务帮助您整理日常任务、设置提醒并保持高效。功能包括美观的暗/亮主题、直观的标签导航以及重要任务的持续通知。',
        'about_developer': '开发者：Jwtyar Nariman',
        'theme_dialog_title': '选择应用主题',
        'theme_system_default': '系统默认'
    }
}

if __name__ == "__main__":
    print("Adding missing strings to incomplete locales...")
    
    for locale_code, translations in locale_translations.items():
        add_missing_strings_to_locale(locale_code, locale_code, translations)
    
    print("\n✅ All missing strings added!")

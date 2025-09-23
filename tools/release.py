#!/bin/python

import os
import subprocess
import sys
import zipfile
from urllib.request import urlretrieve


# git diff should be empty, and there should be no errors
def check_git():
    result = subprocess.run(["git", "diff", "--name-only"], capture_output=True)
    if result.returncode != 0 or len(result.stdout) != 0:
        cont = input("uncommitted changes found, continue? [y/N] ")
        if cont != "y":
            sys.exit()


# download and update translations
def update_translations():
    url = "https://translate.codeberg.org/download/sociakeyboard/?format=zip"
    zip_file_name = "translations.zip"
    urlretrieve(url, zip_file_name)
    # extract all in sociakeyboard/sociakeyboard/app/src/main/res and sociakeyboard/sociakeyboard/fastlane/metadata
    with zipfile.ZipFile(zip_file_name, "r") as f:
        for file in f.filelist:
            if not file.filename.startswith("sociakeyboard/sociakeyboard/app/src/main/res")\
                    and not file.filename.startswith("sociakeyboard/sociakeyboard/fastlane/metadata"):
                continue
            file.filename = file.filename.replace("sociakeyboard/sociakeyboard/", "")
            f.extract(file)
    os.remove(zip_file_name)


# git diff to make sure default strings are the same
def check_default_values_diff():
    result = subprocess.run(["git", "diff", "--name-only", "app/src/main/res/values"], capture_output=True)
    if result.returncode != 0 or len(result.stdout) != 0:
        raise ValueError("default strings changed after translation import, something is wrong")


def read_dicts_readme() -> list[str]:
    dicts_readme_file = "../dictionaries/README.md"
    if os.path.isfile(dicts_readme_file):
        f = open(dicts_readme_file)
        lines = f.readlines()
        f.close()
        return lines
    readme_url = "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/README.md"
    tmp_readme = "dicts_readme_tmp.md"
    urlretrieve(readme_url, tmp_readme)
    f = open(tmp_readme)
    lines = f.readlines()
    f.close()
    os.remove(tmp_readme)
    return lines


# generate a list of dictionaries available in the dictionaries repository at (https://codeberg.org/Helium314/aosp-dictionaries
# for convenient linking when adding dictionaries in SociaKeyboard.
def update_dict_list():
    lines = read_dicts_readme()
    mode = 0
    dicts = []
    for line in lines:
        line = line.strip()
        if line.startswith("#"):
            if line == "# Dictionaries":
                mode = 1
            elif line == "# Experimental dictionaries":
                mode = 2
            else:
                mode = 0
        if mode == 0 or not line.startswith("*"):
            continue
        dict_name = line.split("]")[1].split("(")[1].split(")")[0].split("/")[-1].split(".dict")[0]
        (dict_type, locale) = dict_name.split("_", 1)
        if "_" in locale:
            sp = locale.split("_")
            locale = sp[0]
            for s in sp[1:]:
                locale = locale + "_" + s.upper()
        if mode == 2:
            dicts.append(f"{dict_type},{locale},exp\n")
        else:
            dicts.append(f"{dict_type},{locale},\n")
    target_file = "app/src/main/assets/dictionaries_in_dict_repo.csv"
    with open(target_file, 'w') as f:
        f.writelines(dicts)


# check whether there is a changelog file for current version and print result and version code
def check_changelog():
    changelog_dir = "fastlane/metadata/android/en-US/changelogs"
    assert os.path.isdir(changelog_dir)
    filenames = []
    for file in os.scandir(changelog_dir):
        filenames.append(file.name)
    filenames.sort()
    changelog_version = filenames[-1].replace(".txt", "")
    version = ""
    with open("app/build.gradle.kts") as f:
        for line in f:
            line = line.lstrip()
            if line.startswith("versionCode"):
                version = line.split(" ")[2].rstrip()
                break
    if changelog_version == version:
        print("changelog for", version, "exists")
    else:
        print("changelog for", version, "does not exist")


def main():
    if os.getcwd().endswith("tools"):
        os.chdir("../")
    check_git()
    update_translations()
    check_default_values_diff()
    update_dict_list()
    check_changelog()


if __name__ == "__main__":
    main()

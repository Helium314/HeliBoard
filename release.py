#!/bin/python

import os
import subprocess
import zipfile
from urllib.request import urlretrieve


# git diff should be empty, and there should be no errors
def check_git():
    result = subprocess.run(["git", "diff", "--name-only"], capture_output=True)
    if result.returncode != 0 or len(result.stdout) != 0:
        raise ValueError("uncommitted changes")


# download and update translations
def update_translations():
    url = "https://translate.codeberg.org/download/heliboard/?format=zip"
    zip_file_name = "translations.zip"
    urlretrieve(url, zip_file_name)
    # extract all in heliboard/heliboard/app/src/main/res
    with zipfile.ZipFile(zip_file_name, "r") as f:
        for file in f.filelist:
            if not file.filename.startswith("heliboard/heliboard/app/src/main/res"):
                continue
            file.filename = file.filename.replace("heliboard/heliboard/", "")
            f.extract(file)
    os.remove(zip_file_name)


# git diff to make sure default strings are the same
def check_default_values_diff():
    result = subprocess.run(["git", "diff", "--name-only", "app/src/main/res/values"], capture_output=True)
    if result.returncode != 0 or len(result.stdout) != 0:
        raise ValueError("default strings changed after translation import, something is wrong")


# run that task
def update_dict_list():
#    gradle = "gradlew"  # Linux
#    gradle = "gradlew.bat"  # Windows
    gradle = "../../builder/realgradle.sh"  # weird path for historic reasons
    result = subprocess.run([gradle, ":tools:make-dict-list:makeDictList"])
    assert result.returncode == 0


# check whether there is a changelog file for current version and print result and version code
def check_changelog():
    changelog_dir = "fastlane/metadata/android/en-US/changelogs"
    assert os.path.isdir(changelog_dir)
    filenames = list(os.scandir(changelog_dir))
    filenames.sort()
    changelog_version = filenames[-1].name.replace(".txt", "")
    version = ""
    with open("app/build.gradle") as f:
        for line in f:
            line = line.lstrip()
            if line.startswith("versionCode"):
                version = line.split(" ")[1].rstrip()
                break
    if changelog_version == version:
        print("changelog for", version, "exists")
    else:
        print("changelog for", version, "does not exist")


def main():
    check_git()
    update_translations()
    check_default_values_diff()
    update_dict_list()
    check_changelog()


if __name__ == "__main__":
    main()

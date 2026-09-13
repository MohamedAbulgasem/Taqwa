#!/usr/bin/env python3
"""Copies the Indonesian Compose strings from values-id to values-in.

Compose Multiplatform resolves the language qualifier from `Locale.getLanguage()` on Android,
which still returns the legacy ISO code "in" for Indonesian, and from the BCP-47 "id" on iOS.
One file cannot satisfy both, so values-id is the source of truth and values-in its exact copy;
tools/i18n-check.py fails when they drift. Run after editing values-id.
"""
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "shared/src/commonMain/composeResources/values-id/strings.xml")
DST = os.path.join(ROOT, "shared/src/commonMain/composeResources/values-in/strings.xml")
os.makedirs(os.path.dirname(DST), exist_ok=True)
shutil.copyfile(SRC, DST)
print("values-in/strings.xml refreshed from values-id")

#!/usr/bin/env python3
"""Upload an Android App Bundle to a Google Play track.

Called by scripts/release-android.sh, and usable on its own from anywhere that
holds a Play service account key, which in practice means the VPS rather than
a laptop.

    play-upload.py --package com.flegm.mercato --track alpha \\
        --aab app-release.aab --version-code 5 --version-name 1.0.4 \\
        --key /path/to/play-sa.json [--notes notes.md]

An edit is a transaction: everything below happens inside one, and the commit
at the end is what makes it real. Any failure deletes the edit, so a half-done
release does not sit in the console waiting to confuse whoever opens it next.

Release notes come from a markdown file split by locale headers:

    ## fr-FR
    Ce qui change...

    ## en-US
    What changed...

Without --notes the release carries none, which Play allows and testers read
as "nothing worth saying".
"""

import argparse
import json
import re
import sys


def parse_notes(path):
    """{locale: text} from a file of '## locale' sections."""
    if not path:
        return []
    raw = open(path, encoding="utf-8").read()
    parts = re.split(r"^##\s+([A-Za-z]{2}-[A-Za-z]{2})\s*$", raw, flags=re.M)
    # re.split with one group yields [preamble, locale, body, locale, body...]
    notes = []
    for locale, body in zip(parts[1::2], parts[2::2]):
        text = body.strip()
        if not text:
            continue
        if len(text) > 500:
            sys.exit("error: release notes for %s are %d characters; Play allows 500"
                     % (locale, len(text)))
        notes.append({"language": locale, "text": text})
    if not notes:
        sys.exit("error: no '## locale' section found in %s" % path)
    return notes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", required=True)
    ap.add_argument("--track", required=True)
    ap.add_argument("--aab", required=True)
    ap.add_argument("--version-code", type=int, required=True)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--key", required=True)
    ap.add_argument("--notes")
    ap.add_argument("--draft", action="store_true",
                    help="stage the release without sending it to testers")
    args = ap.parse_args()

    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    notes = parse_notes(args.notes)
    creds = service_account.Credentials.from_service_account_file(
        args.key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    svc = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

    edit = svc.edits().insert(body={}, packageName=args.package).execute()
    eid = edit["id"]
    print("    edit %s" % eid)
    try:
        media = MediaFileUpload(args.aab, mimetype="application/octet-stream",
                                resumable=True)
        bundle = svc.edits().bundles().upload(
            packageName=args.package, editId=eid, media_body=media).execute()
        got = int(bundle["versionCode"])
        print("    uploaded version code %d (sha1 %s)" % (got, bundle.get("sha1")))
        # The bundle decides its own version code, from its manifest. If it is
        # not the one the caller bumped, the file on disk is not the file the
        # build just made, and shipping it would put a stale app on the track
        # under a fresh number.
        if got != args.version_code:
            raise RuntimeError(
                "version code mismatch: the bundle says %d, this release is %d. "
                "The .aab is not the one this run built." % (got, args.version_code))

        release = {
            "name": "%d (%s)" % (got, args.version_name),
            "versionCodes": [str(got)],
            "status": "draft" if args.draft else "completed",
        }
        if notes:
            release["releaseNotes"] = notes
        result = svc.edits().tracks().update(
            packageName=args.package, editId=eid, track=args.track,
            body={"releases": [release]}).execute()
        print("    track %s <- %s" % (
            result["track"], [r["versionCodes"] for r in result["releases"]]))

        svc.edits().validate(packageName=args.package, editId=eid).execute()
        svc.edits().commit(packageName=args.package, editId=eid).execute()
        print("    committed. %d (%s) is on %s%s." % (
            got, args.version_name, args.track, " as a draft" if args.draft else ""))
    except Exception as exc:
        print("error: %s" % exc, file=sys.stderr)
        try:
            svc.edits().delete(packageName=args.package, editId=eid).execute()
            print("    the edit was rolled back; nothing changed on Play",
                  file=sys.stderr)
        except Exception:
            print("    the edit could not be rolled back; check the console",
                  file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

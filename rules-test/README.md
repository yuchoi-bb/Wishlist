# ../firestore.rules, tested

`firestore.rules` is the one file nobody deploys from this repository: it is pasted into the
Firebase Console by hand. That makes it the easiest place for a mistake to live unnoticed — the
console accepts anything that parses, and a rule that is merely *wrong* shows up later as data
somebody could read.

So every claim the app and the READMEs make about who can do what is a case here, run against the
real rules engine in the Firestore emulator rather than against a reading of the file.

```bash
cd rules-test
npm install          # downloads firebase-tools
npm test             # starts the emulator, runs the cases, stops it
```

Needs a JDK on the path — the emulator is a Java program — and will fetch the emulator jar on the
first run. Nothing touches the real project: everything runs against `rules-test`, a project id
that exists only in the emulator.

The cases cover the private subtree, every row of the 주인/공유받은 사람 table in `../web/README.md`,
the move a share makes, and the email directory: that a known address resolves, that the collection
cannot be listed, and that nobody can register an address that isn't theirs.

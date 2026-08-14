# Oci Registry Artifact Publisher Sample

This sample acquires one content-addressed OCI registry blob, verifies exact size and SHA-256, publishes
without replacing an existing staging file, and distributes the verified bytes
to a second native process through CoAkka File Lane.

The current product surface is native C11 only. No JVM, Go, Swift, Node,
Python, or .NET addon connector is claimed by this sample.

Run the [native sample](native/README.md) against the exact published addon
archive. The application owns trusted source configuration, credentials,
File Lane authorization grants, retry policy, and lifecycle. The addon owns
bounded acquisition and integrity/no-clobber staging; Runtime owns delivery.

#ifndef COAKKA_HTTP_TEST_TLS_MATERIAL_H
#define COAKKA_HTTP_TEST_TLS_MATERIAL_H

/* Disposable public test identities for loopback only. The root keys are
 * deliberately absent. The two independent roots make trust replacement
 * observable without requiring a cryptographic tool on the consumer host.
 * The fixed certificate window is 2024-01-01 through 2045-01-01 UTC. */

static const char test_tls_root_a[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBoTCCAUigAwIBAgIUOhiTQhly5T77sae7bnyBQb8SxoYwCgYIKoZIzj0EAwIw\n"
    "HTEbMBkGA1UEAwwSQ29Ba2thIHRlc3Qgcm9vdCBhMB4XDTI0MDEwMTAwMDAwMFoX\n"
    "DTQ1MDEwMTAwMDAwMFowHTEbMBkGA1UEAwwSQ29Ba2thIHRlc3Qgcm9vdCBhMFkw\n"
    "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEwM9gLXwMLqJALmnQF8GUMpn5IVISmOsL\n"
    "eMZkk2eJqWaTyeGvHz2csflh0qy0ozbAUdEt/nJIctIlYMDklxr7O6NmMGQwHQYD\n"
    "VR0OBBYEFLjOhSjHc0BqoVmzuGQqIhNUWDqGMB8GA1UdIwQYMBaAFLjOhSjHc0Bq\n"
    "oVmzuGQqIhNUWDqGMBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgEG\n"
    "MAoGCCqGSM49BAMCA0cAMEQCIE/BAbtdA+6ibAXk6+FAK+RD3T6m6ooBuPlbrRia\n"
    "26vcAiBDi2oUMSiWoEdPHokKgQp9ecjfZbCf4qliVjTGf0a1hA==\n"
    "-----END CERTIFICATE-----\n";

static const char test_tls_certificate_a[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIB0DCCAXagAwIBAgIUfvuMMBjd4fKkHi4OlrKtr50HtngwCgYIKoZIzj0EAwIw\n"
    "HTEbMBkGA1UEAwwSQ29Ba2thIHRlc3Qgcm9vdCBhMB4XDTI0MDEwMTAwMDAwMFoX\n"
    "DTQ1MDEwMTAwMDAwMFowFDESMBAGA1UEAwwJbG9jYWxob3N0MFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAEtkZF5jbI1ykVL7KSS+LKNiGorwFHebZpb6XXMmbGhM++\n"
    "5e1k+iEvFoH+67jV7FkRXjAapOH00errGtpT0m1klqOBnDCBmTAdBgNVHQ4EFgQU\n"
    "x7nkRjVRmr5oJ3z/LyeqaR81tI4wHwYDVR0jBBgwFoAUuM6FKMdzQGqhWbO4ZCoi\n"
    "E1RYOoYwDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYI\n"
    "KwYBBQUHAwEGCCsGAQUFBwMCMBoGA1UdEQQTMBGCCWxvY2FsaG9zdIcEfwAAATAK\n"
    "BggqhkjOPQQDAgNIADBFAiAISnYCy0EBdlReegwtT9UythXrQcsFWiNlH0+2LXoY\n"
    "cAIhAOS8tr9wbcXkxS+mh2plQL2DvWlB4Pw2jsZAlzEClJRj\n"
    "-----END CERTIFICATE-----\n";

static const char test_tls_key_a[] =
    "-----BEGIN PRIVATE KEY-----\n"
    "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg93AaBo62nat3a6Oo\n"
    "w2Y2S166cvPBVnSjYrGmymu3X2qhRANCAAS2RkXmNsjXKRUvspJL4so2IaivAUd5\n"
    "tmlvpdcyZsaEz77l7WT6IS8Wgf7ruNXsWRFeMBqk4fTR6usa2lPSbWSW\n"
    "-----END PRIVATE KEY-----\n";

static const char test_tls_root_b[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIBoTCCAUigAwIBAgIUM43kzikxtgRzSvn0nfF5DEauwOYwCgYIKoZIzj0EAwIw\n"
    "HTEbMBkGA1UEAwwSQ29Ba2thIHRlc3Qgcm9vdCBiMB4XDTI0MDEwMTAwMDAwMFoX\n"
    "DTQ1MDEwMTAwMDAwMFowHTEbMBkGA1UEAwwSQ29Ba2thIHRlc3Qgcm9vdCBiMFkw\n"
    "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEoyOGQn7Uask6gtJRkEPqpq11Ow+k019V\n"
    "tZJgGLZ/FtQw/ynoWlYixi5HqmLlfQBUBof8XZ4MTK1P4iHtXH0L06NmMGQwHQYD\n"
    "VR0OBBYEFLJQBE8DBQYoFwzfcqrOCTj+6uhEMB8GA1UdIwQYMBaAFLJQBE8DBQYo\n"
    "FwzfcqrOCTj+6uhEMBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgEG\n"
    "MAoGCCqGSM49BAMCA0cAMEQCIFPZ3dJx7Z7pCFDTPR3VF6EaPmRpUfVZ3+F/RBRb\n"
    "Xtn+AiAB+O0SwnzoJh6NxOH4yZ41BE5n+wZ/ea6tCpetvtRdvQ==\n"
    "-----END CERTIFICATE-----\n";

static const char test_tls_certificate_b[] =
    "-----BEGIN CERTIFICATE-----\n"
    "MIIB0DCCAXagAwIBAgIULCplsZkhUIdNI1pwdM0gHk9ntiswCgYIKoZIzj0EAwIw\n"
    "HTEbMBkGA1UEAwwSQ29Ba2thIHRlc3Qgcm9vdCBiMB4XDTI0MDEwMTAwMDAwMFoX\n"
    "DTQ1MDEwMTAwMDAwMFowFDESMBAGA1UEAwwJbG9jYWxob3N0MFkwEwYHKoZIzj0C\n"
    "AQYIKoZIzj0DAQcDQgAEFvukoqNrF/CoJD6L1aKo7QyxZHPnqdMcZkPUPc1GRsxY\n"
    "rBCGoIfZLUoTZ/Q1ofauv7oEsZp2oRDnwlwQpStptKOBnDCBmTAdBgNVHQ4EFgQU\n"
    "iGpzw0EcTq8HTmipi91MA6mDnvwwHwYDVR0jBBgwFoAUslAETwMFBigXDN9yqs4J\n"
    "OP7q6EQwDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYI\n"
    "KwYBBQUHAwEGCCsGAQUFBwMCMBoGA1UdEQQTMBGCCWxvY2FsaG9zdIcEfwAAATAK\n"
    "BggqhkjOPQQDAgNIADBFAiAemF2CIX/PpeB0DBHC6eePhd/MtFNl03ooBzy7Yrl1\n"
    "kQIhAOg9UB8Gpbekuge2jJ1Zb4ev3G1OgxsmiCZpR2UPFAdL\n"
    "-----END CERTIFICATE-----\n";

static const char test_tls_key_b[] =
    "-----BEGIN PRIVATE KEY-----\n"
    "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgeUbF5Zuj6Cl8dCD1\n"
    "l0wY4CkTlaHcUcvl1zc+1z633zShRANCAAQW+6Sio2sX8KgkPovVoqjtDLFkc+ep\n"
    "0xxmQ9Q9zUZGzFisEIagh9ktShNn9DWh9q6/ugSxmnahEOfCXBClK2m0\n"
    "-----END PRIVATE KEY-----\n";

#endif

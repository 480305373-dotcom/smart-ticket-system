param([switch]$Renew)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion -lt [Version]'7.5'){throw 'Local certificate generation requires PowerShell 7.5 or newer.'}
$ticketRoot=Split-Path -Parent $PSScriptRoot
$ticketTls=Join-Path $ticketRoot '.runtime/formal/tls'
New-Item -ItemType Directory -Force $ticketTls | Out-Null
$ticketCertificate=Join-Path $ticketTls 'server.crt'
$ticketKey=Join-Path $ticketTls 'server.key'
$ticketCaFile=Join-Path $ticketTls 'ca.crt'
$ticketExisting=@($ticketCertificate,$ticketKey,$ticketCaFile | Where-Object {Test-Path -LiteralPath $_})
if($ticketExisting.Count -gt 0 -and -not $Renew){
    if($ticketExisting.Count -ne 3){throw 'Incomplete local TLS files; inspect .runtime/formal/tls before explicit renewal.'}
    $ticketLoaded=[System.Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPemFile($ticketCertificate,$ticketKey)
    try{if($ticketLoaded.NotAfter -lt (Get-Date).AddDays(7)){throw 'Local certificate expires soon; renew explicitly with -Renew.'}}
    finally{$ticketLoaded.Dispose()}
    Write-Host 'Existing local TLS certificate and matching key verified.'
    return
}
$ticketCaKey=[System.Security.Cryptography.RSA]::Create(3072)
$ticketServerKey=[System.Security.Cryptography.RSA]::Create(2048)
$ticketCa=$null
$ticketLeaf=$null
try{
    $ticketHash=[System.Security.Cryptography.HashAlgorithmName]::SHA256
    $ticketPadding=[System.Security.Cryptography.RSASignaturePadding]::Pkcs1
    $ticketCaRequest=[System.Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=Smart Ticket Local CA',$ticketCaKey,$ticketHash,$ticketPadding)
    $ticketCaRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($true,$false,0,$true))
    $ticketCaRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new([System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyCertSign,$true))
    $ticketCaRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509SubjectKeyIdentifierExtension]::new($ticketCaRequest.PublicKey,$false))
    $ticketCa=$ticketCaRequest.CreateSelfSigned([DateTimeOffset]::Now.AddMinutes(-5),[DateTimeOffset]::Now.AddYears(5))
    $ticketRequest=[System.Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=localhost',$ticketServerKey,$ticketHash,$ticketPadding)
    $ticketNames=[System.Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
    $ticketNames.AddDnsName('localhost')
    $ticketNames.AddIpAddress([System.Net.IPAddress]::Loopback)
    $ticketNames.AddIpAddress([System.Net.IPAddress]::IPv6Loopback)
    $ticketRequest.CertificateExtensions.Add($ticketNames.Build())
    $ticketRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($false,$false,0,$true))
    $ticketRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509SubjectKeyIdentifierExtension]::new($ticketRequest.PublicKey,$false))
    $ticketRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509AuthorityKeyIdentifierExtension]::CreateFromCertificate($ticketCa,$true,$false))
    $ticketRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(([System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment),$true))
    $ticketOids=[System.Security.Cryptography.OidCollection]::new()
    [void]$ticketOids.Add([System.Security.Cryptography.Oid]::new('1.3.6.1.5.5.7.3.1'))
    $ticketRequest.CertificateExtensions.Add([System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new($ticketOids,$true))
    $ticketSerial=[System.Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
    $ticketSerial[0]=$ticketSerial[0] -band 0x7f
    $ticketLeaf=$ticketRequest.Create($ticketCa,[DateTimeOffset]::Now.AddMinutes(-5),[DateTimeOffset]::Now.AddYears(1),$ticketSerial)
    [IO.File]::WriteAllText($ticketCertificate,$ticketLeaf.ExportCertificatePem()+"`n"+$ticketCa.ExportCertificatePem()+"`n")
    [IO.File]::WriteAllText($ticketKey,$ticketServerKey.ExportPkcs8PrivateKeyPem()+"`n")
    [IO.File]::WriteAllText($ticketCaFile,$ticketCa.ExportCertificatePem()+"`n")
    if($IsLinux){& chmod 600 $ticketKey}
    Write-Host 'Created local CA and localhost/loopback TLS certificate; system trust stores were not changed.'
} finally{
    if($ticketLeaf){$ticketLeaf.Dispose()}
    if($ticketCa){$ticketCa.Dispose()}
    $ticketCaKey.Dispose()
    $ticketServerKey.Dispose()
}

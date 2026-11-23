$ErrorActionPreference = 'Stop'

$secureKey = Read-Host 'Cole sua chave da DeepL API (a entrada fica oculta)' -AsSecureString
if ($secureKey.Length -eq 0) {
    throw 'A chave da DeepL API não pode estar vazia.'
}

$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $env:DEEPL_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    $secureKey.Dispose()
}

try {
    Set-Location $PSScriptRoot
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "O backend encerrou com código $LASTEXITCODE."
    }
}
finally {
    Remove-Item Env:DEEPL_API_KEY -ErrorAction SilentlyContinue
}

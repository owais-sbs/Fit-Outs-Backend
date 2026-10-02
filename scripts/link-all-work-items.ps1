$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$script:session = $null

function Ensure-Login {
  Invoke-WebRequest -Uri "$base/auth/login" -Method POST -ContentType "application/json" `
    -Body '{"email":"admin@fitouts.demo","password":"123456"}' -SessionVariable sess -UseBasicParsing -TimeoutSec 60 | Out-Null
  $script:session = $sess
}

function Invoke-Api {
  param(
    [string]$Uri,
    [string]$Method = "GET",
    [Parameter(Mandatory = $false)]
    [string]$Body,
    [int]$MaxRetries = 12
  )
  $attempt = 0
  $hasBody = $PSBoundParameters.ContainsKey("Body") -and -not [string]::IsNullOrEmpty($Body)
  while ($true) {
    $attempt++
    try {
      if ($null -eq $script:session) { Ensure-Login }
      if ($hasBody) {
        return Invoke-WebRequest -Uri $Uri -Method $Method -ContentType "application/json" -Body $Body -WebSession $script:session -UseBasicParsing -TimeoutSec 60
      }
      return Invoke-WebRequest -Uri $Uri -Method $Method -WebSession $script:session -UseBasicParsing -TimeoutSec 60
    } catch {
      $status = $null
      try { $status = [int]$_.Exception.Response.StatusCode } catch {}
      if ($status -ge 400 -and $status -lt 500) { throw }
      if ($attempt -ge $MaxRetries) { throw }
      $delay = [Math]::Min(45, 3 * $attempt)
      Write-Host "Retry $attempt/${MaxRetries}: $($_.Exception.Message) (sleep ${delay}s)"
      Start-Sleep -Seconds $delay
      try { Ensure-Login } catch {}
    }
  }
}

Ensure-Login

# --- Materials by code ---
$matsResp = Invoke-Api -Uri "$base/materials/filter?page=0&size=500" -Method POST -Body '{}'
$mats = @(($matsResp.Content | ConvertFrom-Json).data.content)
$codeToId = @{}
foreach ($m in $mats) { $codeToId[$m.materialCode] = $m.id }
Write-Host "Materials loaded: $($mats.Count)"

function Line([string]$code, [double]$qty = 1.0, [double]$wastage = 0.0) {
  if (-not $codeToId.ContainsKey($code)) { return $null }
  return @{ materialId = $codeToId[$code]; quantityPerUnit = $qty; wastagePercent = $wastage }
}

function Match-Materials([string]$name, [string]$desc) {
  $t = ("$name $desc").ToLowerInvariant()
  $lines = New-Object System.Collections.Generic.List[object]

  # Skip patterns — no clear material
  $skipPatterns = @(
    'any item not',
    '2d shop drawing',
    'shop drawing',
    'fire fighting',
    'fire alarm',
    'gas works',
    'removal of',
    'demolish',
    'demolition',
    'provisional amount for design',
    'documentation',
    'as-built',
    'home automation',
    'external and landscaping',
    'flyscreen',
    'xxx'
  )
  foreach ($sp in $skipPatterns) {
    if ($t -like "*$sp*") { return @() }
  }

  # Room-only / tiny labels (no materials) — exact-ish short names
  $roomOnly = @(
    'kitchen','master bathroom','maid''s bathroom','maids bathroom','bathroom 1',
    'other areas','powder room','master bedroom','living room','guest bathroom',
    'common bathroom','internal doors','ground floor','first floor'
  )
  $trimmed = $name.Trim().ToLowerInvariant()
  if ($roomOnly -contains $trimmed) { return @() }
  if ($trimmed -match '^(powder room|guest bathroom|common bathroom|master bathroom|master bedroom|maid.?s bathroom)\s*:') {
    return @()
  }

  function Add([string]$code, [double]$qty = 1.0, [double]$w = 0.0) {
    $l = Line $code $qty $w
    if ($null -ne $l) {
      $exists = $false
      foreach ($x in $lines) { if ($x.materialId -eq $l.materialId) { $exists = $true; break } }
      if (-not $exists) { [void]$lines.Add($l) }
    }
  }

  # --- Lighting (early, exclusive of gypsum/joinery co-tags) ---
  $isLighting = $t -match '\bled\b|\bspotlight\b|\bdownlight\b|cove light|hanging light|wall light|strip light|extra ligghting|extra lighting'
  if ($isLighting) {
    if ($t -match 'ip65') { Add 'PUR-LED-IP65-W' 1.0 0.0 }
    elseif ($t -match 'ip20' -and $t -match 'black') { Add 'PUR-LED-IP20-B' 1.0 0.0 }
    elseif ($t -match 'ip20') { Add 'PUR-LED-IP20-W' 1.0 0.0 }
    elseif ($t -match 'cove') { Add 'CEL-COVE-LED' 1.0 0.0 }
    else { Add 'ELC-DNL-12W' 1.0 0.0 }
    return $lines.ToArray()
  }

  # --- Plumbing pipes (early) ---
  if ($t -match '\bppr\b|\bpex\b' -and $t -match 'pipe') {
    Add 'PLM-PPR-25' 1.0 0.0
    return $lines.ToArray()
  }

  # Architrave
  if ($t -match 'architrave') { Add 'PUR-ARCH-MDF-PU' 1.0 5.0 }

  # Quartz / Thasos countertop
  if ($t -match 'thasos' -or ($t -match 'kozo' -and $t -match 'quartz')) {
    Add 'PUR-QTZ-THASOS-20' 1.0 10.0
  } elseif ($t -match 'quartz' -and ($t -match 'counter|vanity|top')) {
    Add 'STN-QTZ-20' 1.0 10.0
  }

  # Glass balustrade
  if ($t -match 'balustrade' -and $t -match 'glass') { Add 'PUR-GLS-BAL-1752' 1.0 5.0 }

  # Shower glass (fixed panel or swing door)
  if ($t -match 'shower' -and $t -match 'glass') {
    if ($t -match 'fixed|panel') { Add 'PUR-GLS-SHW-FIX' 1.0 5.0 }
    else { Add 'GLS-TMP-10' 1.0 8.0 }
  }

  # Tempered/ribbed glass door / aluminum or powder-coated glass profiles
  if ($t -match 'ribbed glass' -or ($t -match 'glass door' -and $t -match 'stainless')) {
    Add 'GLS-TMP-10' 1.0 8.0
  }
  if (($t -match 'aluminum|aluminium|powder coated|maq\d|maq\s|ht70|al ghurair') -and $t -match 'glass' -and $t -match '(door|window|profile|frame|glazed)') {
    Add 'GLS-SHF-ALU' 1.0 5.0
    Add 'GLS-TMP-10' 1.0 8.0
  }

  # Sliding door track
  if ($t -match 'sliding door') { Add 'GLS-SLD-TRK' 1.0 0.0 }

  # Tiles (include installation-only floor/wall tiles)
  if ($t -match 'rak ceramics' -or ($t -match 'tiles supply' -and $t -match 'rak')) {
    Add 'PUR-TILE-RAK-6060' 1.0 10.0
  } elseif ($t -match 'tile') {
    Add 'FLR-POR-600' 1.0 10.0
    if ($t -match 'install|adhesive|grouting|floor and wall|backsplash|wall tiles|floor tiles') {
      Add 'FIX-ADH-20' 0.04 0.0
    }
  }

  # Vinyl / PVC / SPC floor (SPC approx vinyl plank — only if no better; skip SPC to avoid force)
  if ($t -match 'vinyl|pvc' -and $t -match 'floor') {
    Add 'FLR-VNL-PLK' 1.0 8.0
    Add 'FIX-ADH-20' 0.04 0.0
  }

  # Marble floor / step (not demolition)
  if ($t -match 'marble' -and $t -match 'floor|tile' -and $t -notmatch 'demolition|demolish') {
    Add 'FLR-MRB-TILE' 1.0 12.0
    Add 'FIX-ADH-20' 0.04 0.0
  }

  # Epoxy floor
  if ($t -match 'epoxy' -and $t -match 'floor') { Add 'FLR-EPX-COAT' 1.0 5.0 }

  # Gypsum / false ceiling / MR board (not when only "provision for cove lighting" side note on other works — already handled lighting)
  if ($t -match 'gypsum|mr-h2|moisture resistant' -or ($t -match 'false ceiling') -or ($t -match 'ceiling board')) {
    Add 'CEL-GYP-125' 1.0 5.0
    if ($t -match 'suspension|grid|including suspension') { Add 'CEL-GRD-MTL' 1.0 5.0 }
  }
  if ($t -match 'acoustic' -and $t -match 'ceiling|tile') { Add 'CEL-ACO-TILE' 1.0 5.0 }

  # Partition / new internal wall
  if ($t -match 'gypsum partition|drywall partition|construction of full height internal wall') {
    Add 'PRT-GYP-100' 1.0 5.0
    Add 'PRT-STUD-75' 1.0 5.0
  }
  if ($t -match 'glass partition') { Add 'PRT-GLS-SNG' 1.0 5.0 }
  if ($t -match 'rockwool|insulation') { Add 'PRT-INS-RW50' 1.0 5.0 }
  if ($t -match 'metal stud') { Add 'PRT-STUD-75' 1.0 5.0 }

  # Paint (not just "ready to receive paint")
  if ($t -match 'spray painting|hand painting|paint finish|emulsion|repainted|paint touch|repainting|final coat|jotun|fenoma') {
    if ($t -notmatch 'ready to receive the paint') {
      Add 'PNT-EMU-INT' 1.0 5.0
    }
  }
  if ($t -match 'epoxy paint') { Add 'PNT-EPX-2C' 1.0 5.0 }
  if ($t -match 'primer') { Add 'PNT-PRM-SEL' 1.0 5.0 }
  if ($t -match 'textured' -and $t -match 'wall|finish') { Add 'PNT-TEX-FIN' 1.0 5.0 }

  # Electrical misc
  if ($t -match 'power socket|13a') { Add 'ELC-SKT-13A' 1.0 0.0 }
  if ($t -match 'cable 2\.5|2.5mm') { Add 'ELC-CBL-25' 1.0 0.0 }
  if ($t -match 'distribution board') { Add 'ELC-DB-12W' 1.0 0.0 }
  if ($t -match 'mallia|face plate') { Add 'PUR-FACE-MALLIA' 1.0 0.0 }

  # Plumbing / sanitary
  if ($t -match 'sanitary') {
    if ($t -match 'maid') { Add 'PUR-SAN-MAID-KR' 1.0 0.0 }
    elseif ($t -match 'powder') {
      if ($t -match 'bagno') { Add 'PUR-SAN-PR-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-PR-KR' 1.0 0.0 }
    }
    elseif ($t -match 'guest') {
      if ($t -match 'bagno') { Add 'PUR-SAN-GB-SH-BAGNO' 1.0 0.0 }
    }
    elseif ($t -match 'common') {
      if ($t -match 'bathtub') {
        if ($t -match 'bagno') { Add 'PUR-SAN-CB-BT-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-CB-BT-KR' 1.0 0.0 }
      } elseif ($t -match 'shower') {
        if ($t -match 'bagno') { Add 'PUR-SAN-CB-SH-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-CB-SH-KR' 1.0 0.0 }
      }
    }
    elseif ($t -match 'master') {
      if ($t -match 'double sink') {
        if ($t -match 'bagno') { Add 'PUR-SAN-MB-DS-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-MB-DS-KR' 1.0 0.0 }
      } elseif ($t -match 'single sink') {
        if ($t -match 'bagno') { Add 'PUR-SAN-MB-SS-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-MB-SS-KR' 1.0 0.0 }
      } elseif ($t -match 'stand alone|standalone') {
        if ($t -match 'bagno') { Add 'PUR-SAN-MB-SA-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-MB-SA-KR' 1.0 0.0 }
      } elseif ($t -match 'inset bathtub') {
        if ($t -match 'bagno') { Add 'PUR-SAN-MB-IB-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-MB-IB-KR' 1.0 0.0 }
      } elseif ($t -match 'shower') {
        if ($t -match 'double') {
          if ($t -match 'bagno') { Add 'PUR-SAN-MB-DS-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-MB-DS-KR' 1.0 0.0 }
        } elseif ($t -match 'single') {
          if ($t -match 'bagno') { Add 'PUR-SAN-MB-SS-BAGNO' 1.0 0.0 } else { Add 'PUR-SAN-MB-SS-KR' 1.0 0.0 }
        }
      }
    }
  }

  # Bathroom package names without "sanitary" word — Master Bathroom (shower & ...)
  if ($t -match 'master bathroom\s*\(' -and $lines.Count -eq 0) {
    if ($t -match 'double sink|double wash') { Add 'PUR-SAN-MB-DS-KR' 1.0 0.0 }
    elseif ($t -match 'single sink') { Add 'PUR-SAN-MB-SS-KR' 1.0 0.0 }
    elseif ($t -match 'stand alone|standalone') { Add 'PUR-SAN-MB-SA-KR' 1.0 0.0 }
    elseif ($t -match 'inset bathtub') { Add 'PUR-SAN-MB-IB-KR' 1.0 0.0 }
    elseif ($t -match 'bathtub') { Add 'PUR-SAN-MB-IB-KR' 1.0 0.0 }
  }
  if ($t -match 'common bathroom\s*\(' -and $lines.Count -eq 0) {
    if ($t -match 'bathtub') { Add 'PUR-SAN-CB-BT-KR' 1.0 0.0 }
    elseif ($t -match 'shower') { Add 'PUR-SAN-CB-SH-KR' 1.0 0.0 }
  }
  if ($t -match "maid.?s bathroom\s*\(" -and $lines.Count -eq 0) {
    Add 'PUR-SAN-MAID-KR' 1.0 0.0
  }
  if ($t -match 'guest bathroom\s*\(' -and $lines.Count -eq 0) {
    Add 'PUR-SAN-GB-SH-BAGNO' 1.0 0.0
  }

  if ($t -match 'kitchen sink' -and $t -match 'bagno|mixer') { Add 'PUR-SINK-MIX-BAGNO' 1.0 0.0 }
  elseif ($t -match 'kitchen sink') { Add 'FIX-SNK-SS' 1.0 0.0 }
  if ($t -match 'basin mixer') { Add 'PLM-BSN-MIX' 1.0 0.0 }
  if ($t -match 'wall-hung|wall hung' -and $t -match 'wc|toilet') { Add 'PLM-WC-WH' 1.0 0.0 }
  if ($t -match 'floor drain|drain point|coring') { Add 'PLM-DRN-SS' 1.0 0.0 }

  # Door handles
  if ($t -match 'main door handle' -or ($t -match 'handle' -and $t -match 'main door')) {
    Add 'PUR-HND-MAIN-CHR' 1.0 0.0
  } elseif ($t -match 'internal' -and $t -match 'handle|ironmonger') {
    Add 'PUR-HND-INT-CHR' 1.0 0.0
  } elseif ($t -match 'door handle') {
    Add 'FIX-HND-SS' 1.0 0.0
  }

  if ($t -match 'main door' -and $t -match 'provisional' -and $t -match 'handle') {
    Add 'PUR-HND-MAIN-CHR' 1.0 0.0
  }

  # Joinery / wardrobe / vanity / kitchen cabinets
  $isJoinery = ($t -match 'wardrobe|vanity unit|kitchen cabinet|base cabinet|top cabinet|joinery|\bmdf\b|shutters') -and ($t -notmatch 'spray painting of existing')
  if ($isJoinery -or ($t -match 'vanity' -and $t -match 'shutter|unit')) {
    Add 'JNY-MDF-18' 1.0 8.0
    if ($t -match 'shutter|door|wardrobe|cabinet') { Add 'JNY-HNG-SC' 2.0 0.0 }
    if ($t -match 'drawer|wardrobe') { Add 'JNY-DRW-RUN' 2.0 0.0 }
    if ($t -match 'veneer') { Add 'JNY-VNR-SWD' 1.0 8.0 }
  }

  if ($t -match 'silicone|sealant') { Add 'FIX-SIL-300' 1.0 0.0 }

  # Granite / stone skirting
  if ($t -match 'granite' -and $t -match 'counter|top') { Add 'STN-GRN-30' 1.0 10.0 }
  if ($t -match 'skirting' -and $t -match 'stone|marble|santa luzia|sl101') { Add 'STN-SKT-100' 1.0 5.0 }

  return $lines.ToArray()
}

# --- Load all work item IDs ---
$page = 0
$ids = @()
do {
  $r = Invoke-Api -Uri "$base/work-items/filter?page=$page&size=100" -Method POST -Body '{}'
  $j = ($r.Content | ConvertFrom-Json).data
  foreach ($wi in @($j.content)) { $ids += $wi.id }
  $totalPages = [int]$j.totalPages
  $page++
} while ($page -lt $totalPages)
Write-Host "Work item ids: $($ids.Count)"

function Build-Body($detailObj, $matLines) {
  return (@{
    workItemName = $detailObj.workItemName
    workItemCode = $detailObj.workItemCode
    workItemMasterId = $detailObj.workItemMasterId
    description = $detailObj.description
    ceilingApplicable = [bool]$detailObj.ceilingApplicable
    wallApplicable = [bool]$detailObj.wallApplicable
    floorApplicable = [bool]$detailObj.floorApplicable
    unitType = $detailObj.unitType
    defaultRate = $(if ($null -eq $detailObj.defaultRate) { 0 } else { $detailObj.defaultRate })
    subcontractorRate = $(if ($null -eq $detailObj.subcontractorRate) { 0 } else { $detailObj.subcontractorRate })
    markupPercentage = $(if ($null -eq $detailObj.markupPercentage) { 0 } else { $detailObj.markupPercentage })
    costPrice = $(if ($null -eq $detailObj.costPrice) { 0 } else { $detailObj.costPrice })
    costPriceOverride = $true
    sellingPriceOverride = $true
    materialLines = $matLines
    scopeTagIds = @($detailObj.scopeTags | ForEach-Object { $_.id })
    quantityFormulaType = $(if ($detailObj.quantityFormulaType) { $detailObj.quantityFormulaType } else { "MANUAL" })
    icon = $(if ($detailObj.icon) { $detailObj.icon } else { "Wrench" })
    colorTag = $(if ($detailObj.colorTag) { $detailObj.colorTag } else { "blue" })
  } | ConvertTo-Json -Depth 8 -Compress)
}

$linked = 0
$skippedAlready = 0
$skippedNoMatch = 0
$errors = 0
$linkedNames = New-Object System.Collections.Generic.List[string]
$skippedNames = New-Object System.Collections.Generic.List[string]

$i = 0
foreach ($id in $ids) {
  $i++
  if ($i % 25 -eq 0) { Write-Host "Progress $i / $($ids.Count) (linked=$linked skipEmpty=$skippedNoMatch already=$skippedAlready)" }

  try {
    $detailResp = Invoke-Api -Uri "$base/work-items/$id"
    $detail = ($detailResp.Content | ConvertFrom-Json).data
  } catch {
    $errors++
    Write-Host "ERR get $id : $($_.Exception.Message)"
    continue
  }

  $existing = @($detail.materialLines)
  $matched = @(Match-Materials $detail.workItemName ($detail.description | Out-String))

  $text = ("$($detail.workItemName) $($detail.description)").ToLowerInvariant()
  $needsFix = $false
  if ($existing.Count -gt 0) {
    $codes = @($existing | ForEach-Object { $_.materialCode })
    if (($text -match '\bppr\b|\bpex\b') -and ($codes -contains 'ELC-DNL-12W')) { $needsFix = $true }
    if (($text -match '\bled\b|strip light|spotlight') -and (($codes -contains 'JNY-MDF-18') -or ($codes -contains 'CEL-GYP-125'))) { $needsFix = $true }
  }

  if ($matched.Count -eq 0) {
    $skippedNoMatch++
    if ($skippedNames.Count -lt 40) { [void]$skippedNames.Add($detail.workItemName) }
    continue
  }

  if ($existing.Count -gt 0 -and -not $needsFix) {
    $skippedAlready++
    continue
  }

  try {
    if ($needsFix) {
      Invoke-Api -Uri "$base/work-items/$id" -Method PUT -Body (Build-Body $detail @()) | Out-Null
      Start-Sleep -Milliseconds 150
    }
    $upd = Invoke-Api -Uri "$base/work-items/$id" -Method PUT -Body (Build-Body $detail $matched)
    if ($upd.StatusCode -ge 200 -and $upd.StatusCode -lt 300) {
      $linked++
      $codeList = @()
      foreach ($ml in $matched) {
        foreach ($kv in $codeToId.GetEnumerator()) {
          if ($kv.Value -eq $ml.materialId) { $codeList += $kv.Key; break }
        }
      }
      $short = $detail.workItemName
      if ($short.Length -gt 70) { $short = $short.Substring(0,70) + "..." }
      [void]$linkedNames.Add("$short => $($codeList -join ', ')")
    } else {
      $errors++
    }
  } catch {
    $errors++
    $msg = $_.ErrorDetails.Message
    if (-not $msg) { $msg = $_.Exception.Message }
    Write-Host "ERR $($detail.workItemName.Substring(0,[Math]::Min(40,$detail.workItemName.Length))): $msg"
  }
  Start-Sleep -Milliseconds 80
}

Write-Host ""
Write-Host "======== SUMMARY ========"
Write-Host "Total: $($ids.Count)"
Write-Host "Newly linked: $linked"
Write-Host "Already had materials: $skippedAlready"
Write-Host "Left empty (no clear match): $skippedNoMatch"
Write-Host "Errors: $errors"
Write-Host ""
Write-Host "--- LINKED (sample) ---"
$linkedNames | Select-Object -First 50 | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "--- SKIPPED SAMPLE ---"
$skippedNames | Select-Object -First 40 | ForEach-Object { Write-Host $_ }

# Spot-check material plan
Write-Host ""
Write-Host "--- Regenerating material plan project 57 ---"
$gen = Invoke-Api -Uri "$base/projects/57/material-plan/generate" -Method POST
$plan = ($gen.Content | ConvertFrom-Json).data
Write-Host "Plan lines: $($plan.lines.Count) sections: $($plan.sections.Count)"
$withMats = @($plan.sections | Where-Object { @($_.lines).Count -gt 0 }).Count
$without = @($plan.sections | Where-Object { @($_.lines).Count -eq 0 }).Count
Write-Host "Sections with materials: $withMats | without: $without"


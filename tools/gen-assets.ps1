# Generates every Trading Desk texture and JSON file: block and item models, block states, textures, loot tables,
# recipes, recipe unlocks, tool tags, names, and the mod icon.
# Usage: powershell -ExecutionPolicy Bypass -File tools\gen-assets.ps1
$ErrorActionPreference = 'Stop'
trap { Write-Host "Error: $_"; exit 1 }
Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot '..\src\main\resources'
$assets = Join-Path $root 'assets\tradingdesk'
$data = Join-Path $root 'data\tradingdesk'
$tex = Join-Path $assets 'textures'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Everything under these is generated, so start clean and leave nothing behind from older versions.
foreach ($dir in @('blockstates', 'items', 'lang', 'models', 'textures')) { Remove-Item -Recurse -Force (Join-Path $assets $dir) -ErrorAction SilentlyContinue }
Remove-Item -Recurse -Force $data, (Join-Path $root 'data\minecraft') -ErrorAction SilentlyContinue

function Write-Json($path, $text) {
	$dir = Split-Path $path
	if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
	[System.IO.File]::WriteAllText($path, ($text.Trim() -replace "`r`n", "`n") + "`n", $utf8)
}

function Save-Bitmap($bmp, $path) {
	$dir = Split-Path $path
	if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
	$bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
}

# 'rrggbb' or 'aarrggbb'
function Color($hex) {
	if ($hex.Length -eq 6) { $hex = 'ff' + $hex }
	return [System.Drawing.Color]::FromArgb([Convert]::ToInt32($hex, 16))
}

function New-Bitmap($w, $h) { return New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }

# Speckled color.
function Noise-Texture($dark, $mid, $light, $path, $seed) {
	$rand = New-Object System.Random $seed
	$bmp = New-Bitmap 16 16
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			$roll = $rand.NextDouble()
			$hex = if ($roll -lt 0.15) { $light } elseif ($roll -lt 0.3) { $dark } else { $mid }
			$bmp.SetPixel($x, $y, (Color $hex))
		}
	}
	Save-Bitmap $bmp $path
}

# Wood planks running along x, with dark seams every 4 rows.
function Wood-Texture($path, $seed) {
	$rand = New-Object System.Random $seed
	$bmp = New-Bitmap 16 16
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			$roll = $rand.NextDouble()
			$hex = if ($y % 4 -eq 3) { '2b1a10' } elseif ($roll -lt 0.12) { '5a3a24' } elseif ($roll -lt 0.3) { '3d2718' } else { '4a301e' }
			$bmp.SetPixel($x, $y, (Color $hex))
		}
	}
	Save-Bitmap $bmp $path
}

# A screen face: a thin bezel around a dark glass middle.
function Screen-Texture($bezel, $glass, $path) {
	$bmp = New-Bitmap 16 16
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			$edge = $x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15
			$bmp.SetPixel($x, $y, (Color $(if ($edge) { $bezel } else { $glass })))
		}
	}
	Save-Bitmap $bmp $path
}

# A keyboard: rows of light keys on a dark base.
function Keyboard-Texture($path) {
	$bmp = New-Bitmap 16 16
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			$key = ($x % 2 -eq 1) -and ($y % 3 -ne 0) -and $x -lt 15
			$bmp.SetPixel($x, $y, (Color $(if ($key) { '5a5f66' } else { '1b1d21' })))
		}
	}
	Save-Bitmap $bmp $path
}

# Candles on a dark screen: [x, top, bottom, up?] for each, bodies 2 wide with a 1 pixel wick.
function Draw-Candles($bmp, $candles, $scale, $offsetX, $offsetY) {
	foreach ($c in $candles) {
		$x, $top, $bottom, $up = $c
		$color = Color $(if ($up) { '26a69a' } else { 'ef5350' })
		for ($y = $top; $y -le $bottom; $y++) {
			for ($dx = 0; $dx -lt 2; $dx++) {
				for ($sy = 0; $sy -lt $scale; $sy++) { for ($sx = 0; $sx -lt $scale; $sx++) {
					$bmp.SetPixel($offsetX + ($x + $dx) * $scale + $sx, $offsetY + $y * $scale + $sy, $color)
				} }
			}
		}
		for ($y = $top - 1; $y -le $bottom + 1; $y++) {
			for ($sy = 0; $sy -lt $scale; $sy++) { for ($sx = 0; $sx -lt [Math]::Max(1, $scale / 2); $sx++) {
				$bmp.SetPixel($offsetX + $x * $scale + [int]($scale / 2) + $sx, $offsetY + $y * $scale + $sy, $color)
			} }
		}
	}
}

$candles = @(@(2, 9, 12, $false), @(5, 7, 10, $true), @(8, 8, 11, $false), @(11, 4, 8, $true))

# ---- Textures ----
Wood-Texture (Join-Path $tex 'block\desk_top.png') 1
Noise-Texture '141518' '1d1f23' '2a2d33' (Join-Path $tex 'block\desk_metal.png') 2
Noise-Texture '0c0d0f' '141518' '1d1f23' (Join-Path $tex 'block\monitor.png') 3
Screen-Texture '26292e' '0d1117' (Join-Path $tex 'block\monitor_screen.png')
Keyboard-Texture (Join-Path $tex 'block\keyboard.png')
Screen-Texture '1d1f23' '0d1117' (Join-Path $tex 'block\chart_screen_front.png')
Noise-Texture '1d1f23' '26292e' '30343a' (Join-Path $tex 'block\chart_screen_back.png') 4

# Chart screen item: a bezel around candles.
$bmp = New-Bitmap 16 16
for ($y = 1; $y -lt 15; $y++) { for ($x = 0; $x -lt 16; $x++) {
	$edge = $x -eq 0 -or $y -eq 1 -or $x -eq 15 -or $y -eq 14
	$bmp.SetPixel($x, $y, (Color $(if ($edge) { '3a3f46' } else { '0d1117' })))
} }
Draw-Candles $bmp @(@(2, 8, 11, $false), @(5, 6, 9, $true), @(8, 7, 10, $false), @(11, 3, 7, $true)) 1 0 0
Save-Bitmap $bmp (Join-Path $tex 'item\chart_screen.png')

# The mod icon: a big chart.
$bmp = New-Bitmap 128 128
for ($y = 0; $y -lt 128; $y++) { for ($x = 0; $x -lt 128; $x++) {
	$edge = $x -lt 6 -or $y -lt 6 -or $x -ge 122 -or $y -ge 122
	$bmp.SetPixel($x, $y, (Color $(if ($edge) { '26292e' } else { '0d1117' })))
} }
Draw-Candles $bmp $candles 8 0 0
Save-Bitmap $bmp (Join-Path $assets 'icon.png')

# ---- Trading desk: a wooden top on metal legs, a monitor at the back, a keyboard and mouse ----
# Modeled facing north: the trader sits on the north side and the monitor faces them.
Write-Json (Join-Path $assets 'models\block\trading_desk.json') @'
{
	"parent": "minecraft:block/block",
	"textures": {
		"particle": "tradingdesk:block/desk_top",
		"top": "tradingdesk:block/desk_top",
		"metal": "tradingdesk:block/desk_metal",
		"monitor": "tradingdesk:block/monitor",
		"screen": "tradingdesk:block/monitor_screen",
		"keyboard": "tradingdesk:block/keyboard"
	},
	"elements": [
		{ "from": [0, 12, 0], "to": [16, 14, 16], "faces": {
			"up": { "texture": "#top" }, "down": { "texture": "#top" },
			"north": { "texture": "#top" }, "south": { "texture": "#top" }, "east": { "texture": "#top" }, "west": { "texture": "#top" } } },
		{ "from": [1, 0, 1], "to": [3, 12, 3], "faces": {
			"north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" }, "down": { "texture": "#metal" } } },
		{ "from": [13, 0, 1], "to": [15, 12, 3], "faces": {
			"north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" }, "down": { "texture": "#metal" } } },
		{ "from": [1, 0, 13], "to": [3, 12, 15], "faces": {
			"north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" }, "down": { "texture": "#metal" } } },
		{ "from": [13, 0, 13], "to": [15, 12, 15], "faces": {
			"north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" }, "down": { "texture": "#metal" } } },
		{ "from": [5, 14, 11], "to": [11, 14.5, 14], "faces": {
			"up": { "texture": "#metal" }, "north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" } } },
		{ "from": [7, 14.5, 12], "to": [9, 18, 13], "faces": {
			"north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" } } },
		{ "from": [1, 15, 11], "to": [15, 24, 12], "faces": {
			"north": { "uv": [0, 0, 16, 16], "texture": "#screen" },
			"south": { "uv": [0, 0, 16, 16], "texture": "#monitor" },
			"east": { "uv": [0, 0, 1, 9], "texture": "#monitor" },
			"west": { "uv": [0, 0, 1, 9], "texture": "#monitor" },
			"up": { "uv": [0, 0, 14, 1], "texture": "#monitor" },
			"down": { "uv": [0, 0, 14, 1], "texture": "#monitor" } } },
		{ "from": [3, 14, 2], "to": [12, 14.5, 5.5], "faces": {
			"up": { "texture": "#keyboard" }, "north": { "texture": "#metal" }, "south": { "texture": "#metal" }, "east": { "texture": "#metal" }, "west": { "texture": "#metal" } } },
		{ "from": [13, 14, 3], "to": [14.5, 14.75, 5], "faces": {
			"up": { "texture": "#monitor" }, "north": { "texture": "#monitor" }, "south": { "texture": "#monitor" }, "east": { "texture": "#monitor" }, "west": { "texture": "#monitor" } } }
	]
}
'@

# ---- Chart screen: a thin panel against the wall behind it; modeled facing north ----
Write-Json (Join-Path $assets 'models\block\chart_screen.json') @'
{
	"parent": "minecraft:block/block",
	"textures": {
		"particle": "tradingdesk:block/chart_screen_back",
		"front": "tradingdesk:block/chart_screen_front",
		"back": "tradingdesk:block/chart_screen_back"
	},
	"elements": [
		{ "from": [0, 0, 15], "to": [16, 16, 16], "faces": {
			"north": { "texture": "#front" },
			"south": { "texture": "#back", "cullface": "south" },
			"east": { "uv": [0, 0, 1, 16], "texture": "#back" },
			"west": { "uv": [0, 0, 1, 16], "texture": "#back" },
			"up": { "uv": [0, 0, 16, 1], "texture": "#back" },
			"down": { "uv": [0, 0, 16, 1], "texture": "#back" } } }
	]
}
'@

foreach ($id in @('trading_desk', 'chart_screen')) {
	Write-Json (Join-Path $assets "blockstates\$id.json") @"
{
	"variants": {
		"facing=north": { "model": "tradingdesk:block/$id" },
		"facing=east": { "model": "tradingdesk:block/$id", "y": 90 },
		"facing=south": { "model": "tradingdesk:block/$id", "y": 180 },
		"facing=west": { "model": "tradingdesk:block/$id", "y": 270 }
	}
}
"@
	Write-Json (Join-Path $data "loot_table\blocks\$id.json") @"
{
	"type": "minecraft:block",
	"pools": [
		{
			"rolls": 1,
			"conditions": [ { "condition": "minecraft:survives_explosion" } ],
			"entries": [ { "type": "minecraft:item", "name": "tradingdesk:$id" } ]
		}
	]
}
"@
}

Write-Json (Join-Path $assets 'items\trading_desk.json') '{ "model": { "type": "minecraft:model", "model": "tradingdesk:block/trading_desk" } }'
Write-Json (Join-Path $assets 'models\item\chart_screen.json') '{ "parent": "minecraft:item/generated", "textures": { "layer0": "tradingdesk:item/chart_screen" } }'
Write-Json (Join-Path $assets 'items\chart_screen.json') '{ "model": { "type": "minecraft:model", "model": "tradingdesk:item/chart_screen" } }'

# ---- Recipes ----
# Desk: redstone and a glass pane for the monitor, dark oak planks for the top, iron legs.
# Chart screens (2): glass panes around redstone, iron nugget corners.
$recipes = @(
	@('trading_desk', '[ "RGR", "PPP", "I I" ]', '"R": "minecraft:redstone", "G": "minecraft:glass_pane", "P": "minecraft:dark_oak_planks", "I": "minecraft:iron_ingot"', 1),
	@('chart_screen', '[ "NGN", "GRG", "NGN" ]', '"N": "minecraft:iron_nugget", "G": "minecraft:glass_pane", "R": "minecraft:redstone"', 2)
)
foreach ($r in $recipes) {
	$id, $pattern, $key, $count = $r
	Write-Json (Join-Path $data "recipe\$id.json") @"
{
	"type": "minecraft:crafting_shaped",
	"category": "misc",
	"pattern": $pattern,
	"key": { $key },
	"result": { "id": "tradingdesk:$id", "count": $count }
}
"@
	Write-Json (Join-Path $data "advancement\recipes\misc\$id.json") @"
{
	"parent": "minecraft:recipes/root",
	"criteria": {
		"has_redstone": { "conditions": { "items": [ { "items": "minecraft:redstone" } ] }, "trigger": "minecraft:inventory_changed" },
		"has_the_recipe": { "conditions": { "recipes": "tradingdesk:$id" }, "trigger": "minecraft:recipe_unlocked" }
	},
	"requirements": [ [ "has_the_recipe", "has_redstone" ] ],
	"rewards": { "recipes": [ "tradingdesk:$id" ] }
}
"@
}

# ---- Tool tags and names ----
Write-Json (Join-Path $root 'data\minecraft\tags\block\mineable\axe.json') '{ "values": [ "tradingdesk:trading_desk" ] }'
Write-Json (Join-Path $root 'data\minecraft\tags\block\mineable\pickaxe.json') '{ "values": [ "tradingdesk:chart_screen" ] }'
Write-Json (Join-Path $assets 'lang\en_us.json') @'
{
	"creativeTab.tradingdesk": "Trading Desk",
	"block.tradingdesk.trading_desk": "Trading Desk",
	"block.tradingdesk.chart_screen": "Chart Screen"
}
'@

Write-Host "Generated the trading desk, chart screen, and icon."

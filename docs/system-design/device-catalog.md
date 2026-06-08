# Industry Device Catalog (400 devices across 20 industries)

Comprehensive seed covering IoT devices used in real-world industrial deployments. Each device is configured with per-sensor capability thresholds in the `capabilities` JSONB column (alert engine uses these instead of global env-var defaults).

```bash
./scripts/seed-industry.sh       # seeds 20 orgs + 400 devices + 48 h telemetry + alerts
docker exec -i sentinel-postgres psql -U sentinel -d sentinel \
  < scripts/unseed-industry.sql  # removes industry data only
```

Each industry org gets one admin user (password `sentinel123`):

| Industry | Organisation slug | Admin username | Devices |
|---|---|---|---|
| Manufacturing / Smart Factory | `manufacturing` | `org-manufacturing-admin` | 20 |
| Cold Chain & Food Safety | `cold-chain` | `org-cold-chain-admin` | 20 |
| Data Center & IT | `datacenter` | `org-datacenter-admin` | 20 |
| Agriculture & Greenhouse | `agriculture` | `org-agriculture-admin` | 20 |
| Healthcare & Pharma | `healthcare` | `org-healthcare-admin` | 20 |
| Energy & Utilities | `energy` | `org-energy-admin` | 20 |
| Smart Building & Facilities | `smart-building` | `org-smart-building-admin` | 20 |
| Logistics & Warehouse | `logistics` | `org-logistics-admin` | 20 |
| Oil & Gas | `oil-gas` | `org-oil-gas-admin` | 20 |
| Mining | `mining` | `org-mining-admin` | 20 |
| Water & Wastewater | `water` | `org-water-admin` | 20 |
| Chemical & Petrochemical | `chemical` | `org-chemical-admin` | 20 |
| Marine & Port | `marine` | `org-marine-admin` | 20 |
| Food & Beverage Processing | `food-beverage` | `org-food-beverage-admin` | 20 |
| Automotive Manufacturing | `automotive` | `org-automotive-admin` | 20 |
| Railway & Transit | `railway` | `org-railway-admin` | 20 |
| Semiconductor Fab | `semiconductor` | `org-semiconductor-admin` | 20 |
| Hospitality & Hotels | `hospitality` | `org-hospitality-admin` | 20 |
| Retail & Supermarket | `retail` | `org-retail-admin` | 20 |
| Construction & Infrastructure | `construction` | `org-construction-admin` | 20 |

## Device Catalog

### 1. Manufacturing / Smart Factory (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `mfg-assembly-line-01` | Assembly Line Temperature, Vibration & Fume Monitor | TEMPERATURE · VIBRATION_G · SMOKE_PPM | Temp warn 78°C / crit 85°C · Vib warn 4g / crit 7g · Smoke warn 150 / crit 250 ppm |
| `mfg-cnc-machine-01` | CNC Machine Health Monitor — Predictive Maintenance | TEMPERATURE · VIBRATION_G · SOUND_DB · CURRENT_A | Temp crit 78°C · Vib crit 6g · Sound crit 105 dB · Current crit 48A |
| `mfg-air-quality-01` | Factory Floor Multi-Gas Air Quality Station | CO2_PPM · CO_PPM · VOC_INDEX · PM25 | CO₂ crit 2000 ppm · CO crit 35 ppm · VOC crit 350 |
| `mfg-motor-drive-01` | Motor Drive Electrical Health & Power Monitor | TEMPERATURE · CURRENT_A · VOLTAGE_V · POWER_W | Temp crit 85°C · Current crit 95A · Power crit 48 kW |
| `mfg-compressor-01` | Industrial Air Compressor — Pressure & Health | TEMPERATURE · PRESSURE · VIBRATION_G · CURRENT_A | Pressure warn 900 / crit 1100 kPa · Temp crit 80°C |
| `mfg-welding-01` | Welding Bay Fume & Safety Monitor | SMOKE_PPM · CO_PPM · VOC_INDEX | Smoke crit 400 ppm · CO crit 80 ppm |
| `mfg-conveyor-01` | Conveyor Belt Motion & Mechanical Health | MOTION · VIBRATION_G · TEMPERATURE | Vib warn 2.5g / crit 4g · Temp crit 65°C |
| `mfg-utility-room-01` | Plant Utility Room Environmental Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | CO₂ crit 1500 ppm · Temp crit 38°C |
| `mfg-boiler-01` | Industrial Steam Boiler Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · CO_PPM | Pressure crit 1600 kPa · Temp crit 200°C · CO crit 50 ppm |
| `mfg-chiller-01` | Industrial Chiller & Cooling Tower Monitor | TEMPERATURE · PRESSURE · CURRENT_A · FLOW_LPM | Temp crit 35°C (supply water) · Current crit 150A · Pressure crit 900 kPa |
| `mfg-paint-booth-01` | Paint Booth VOC & Climate Monitor | TEMPERATURE · HUMIDITY · VOC_INDEX · PM25 | VOC crit 400 (LEL proxy) · Humidity crit 75% · PM2.5 crit 20 µg/m³ |
| `mfg-robot-arm-01` | Industrial Robot Arm Health Monitor | CURRENT_A · VIBRATION_G · TEMPERATURE | Current crit 80A · Vib crit 5g · Temp crit 85°C |
| `mfg-cleanroom-01` | Electronics Production Cleanroom ISO Class 8 Monitor | TEMPERATURE · HUMIDITY · PM25 · PM10 · PRESSURE | PM2.5 crit 100 µg/m³ · PM10 crit 300 µg/m³ · Humidity crit 55% |
| `mfg-dust-collector-01` | Industrial Dust Collection System Monitor | PRESSURE · FLOW_LPM · VIBRATION_G · CURRENT_A | Pressure crit 3500 Pa ABOVE (filter clog) · Vib crit 4g · Current crit 45A |
| `mfg-hydraulic-press-01` | Hydraulic Press Health Monitor | PRESSURE · TEMPERATURE · VIBRATION_G · CURRENT_A | Pressure warn 18000 / crit 22000 kPa · Temp crit 70°C · Vib crit 5g |
| `mfg-furnace-01` | Industrial Heat Treatment Furnace Monitor | TEMPERATURE · PRESSURE · CO_PPM | Temp warn 900 / crit 950°C · Pressure crit 500 kPa · CO crit 100 ppm |
| `mfg-cooling-tower-01` | Cooling Tower Water Quality & Performance Monitor | TEMPERATURE · FLOW_LPM · WATER_LEVEL_PCT · PH | Temp crit 35°C · pH warn 8.5 / crit 9.0 ABOVE · Level crit 15% BELOW |
| `mfg-transformer-room-01` | Factory Transformer Room Environment Monitor | TEMPERATURE · HUMIDITY · CURRENT_A · VOLTAGE_V | Temp crit 55°C · Humidity crit 80% · Current crit 800A |
| `mfg-compressed-air-01` | Compressed Air System Quality & Dew Point Monitor | PRESSURE · FLOW_LPM · HUMIDITY · TEMPERATURE | Pressure crit 500 kPa BELOW · Humidity crit 40% (dew point proxy) · Temp crit 45°C |
| `mfg-wastewater-01` | Factory Wastewater Treatment Monitor | PH · WATER_LEVEL_PCT · TEMPERATURE · FLOW_LPM | pH warn 9.0 / crit 10.0 ABOVE · Level crit 85% ABOVE · Flow crit 500 LPM ABOVE |

### 2. Cold Chain & Food Safety (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `cold-blast-freezer-01` | Industrial Blast Freezer — Deep Freeze (-18°C) | TEMPERATURE · HUMIDITY | Temp warn -15°C / crit -10°C ABOVE |
| `cold-walkin-fridge-01` | Walk-in Refrigerator +2 to +4°C | TEMPERATURE · HUMIDITY | Temp warn 6°C / crit 8°C |
| `cold-display-case-01` | Refrigerated Retail Display Case | TEMPERATURE · HUMIDITY | Temp warn 6°C / crit 8°C |
| `cold-pasteuriser-01` | HTST Pasteurisation Line (72°C/15s) | TEMPERATURE · HUMIDITY · PRESSURE | Temp warn 73°C / crit 72°C BELOW (drop = failure) |
| `cold-transport-01` | Refrigerated Vehicle Fleet Tracker | TEMPERATURE · HUMIDITY · VIBRATION_G | Temp crit 7°C · Vib crit 7g (road shock) |
| `cold-wine-cellar-01` | Wine Cellar Precision Climate (12°C / 65%RH) | TEMPERATURE · HUMIDITY · LIGHT_LUX | Temp crit 17°C · Light crit 200 lux (UV damage) |
| `cold-reefer-container-01` | ISO Shipping Reefer Container Climate Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn -17°C / crit -15°C ABOVE · CO₂ crit 5000 ppm |
| `cold-meat-smoker-01` | Meat Smoking & Curing Chamber Monitor | TEMPERATURE · HUMIDITY · SMOKE_PPM | Temp warn 75°C / crit 82°C · Humidity crit 25% BELOW |
| `cold-ice-hardening-01` | Ice Cream Hardening Tunnel (-35°C) Monitor | TEMPERATURE · HUMIDITY | Temp warn -33°C / crit -30°C ABOVE · Humidity crit 90% |
| `cold-loading-bay-01` | Cold Store Loading Bay Temperature Bridge Monitor | TEMPERATURE · HUMIDITY · MOTION | Temp crit 10°C · Humidity crit 88% |
| `cold-nitrogen-tank-01` | Liquid Nitrogen Cryogenic Storage Tank Monitor | WATER_LEVEL_PCT · TEMPERATURE · PRESSURE · O2_PCT | Level crit 15% BELOW · O₂ crit 19.5% BELOW (asphyxiation) · Temp crit -170°C ABOVE |
| `cold-dairy-processor-01` | Dairy Processing & Homogeniser Monitor | TEMPERATURE · PRESSURE · FLOW_LPM | Temp crit 8°C ABOVE (raw milk) · Pressure crit 18000 kPa · Flow crit 500 LPM BELOW |
| `cold-fish-processing-01` | Fish & Seafood Processing Room Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp crit 5°C ABOVE · Humidity crit 95% · CO₂ crit 3000 ppm |
| `cold-pharmaceutical-transport-01` | Pharmaceutical Cold Chain Active Transport Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G | Temp warn 5°C / crit 8°C ABOVE · Vib crit 6g (shock) |
| `cold-ice-rink-01` | Ice Rink Refrigeration Plant Monitor | TEMPERATURE · PRESSURE · CURRENT_A · FLOW_LPM | Temp crit -3°C ABOVE (ice surface) · Pressure crit 1200 kPa · Current crit 200A |
| `cold-cheese-aging-01` | Cheese Aging Cave Climate Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn 12°C / crit 15°C ABOVE · Humidity warn 92% / crit 96% ABOVE · CO₂ crit 4000 ppm |
| `cold-frozen-food-01` | Frozen Food Production Spiral Freezer Monitor | TEMPERATURE · HUMIDITY · FLOW_LPM | Temp warn -35°C / crit -30°C ABOVE · Humidity crit 90% |
| `cold-banana-ripening-01` | Banana Ripening Room Climate Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn 15°C / crit 18°C · CO₂ warn 1000 / crit 3000 ppm |
| `cold-blood-plasma-01` | Blood Plasma Ultra-Low Temperature Freezer Monitor | TEMPERATURE · HUMIDITY | Temp warn -28°C / crit -25°C ABOVE · Humidity crit 65% |
| `cold-vaccine-transport-01` | Vaccine Cold Box & Last-Mile Transport Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G | Temp warn 7°C / crit 8°C ABOVE · Vib crit 5g |

### 3. Data Center & IT Infrastructure (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `dc-server-rack-01` | Server Rack Thermal — Hot Aisle Containment | TEMPERATURE · HUMIDITY | Temp warn 35°C / crit 40°C |
| `dc-ups-01` | UPS Battery System — 48V DC Bus | BATTERY_PCT · BATTERY_V · CURRENT_A · TEMPERATURE | Battery crit 10% BELOW · Temp crit 46°C |
| `dc-pdu-01` | Smart PDU — 3-Phase 30 kW | POWER_W · CURRENT_A · VOLTAGE_V · ENERGY_KWH | Power crit 28.5 kW · Voltage crit 208V BELOW |
| `dc-leak-sensor-01` | Raised-Floor Water Leak Detector | WATER_LEVEL_PCT · TEMPERATURE | Water warn 1% / crit 5% ABOVE |
| `dc-crac-unit-01` | Computer Room Air Conditioning (CRAC) | TEMPERATURE · HUMIDITY · PRESSURE | Temp crit 28°C · Humidity crit 65% |
| `dc-generator-01` | Emergency Diesel Generator | TEMPERATURE · VIBRATION_G · CURRENT_A · VOLTAGE_V | Temp crit 93°C · Voltage crit 205V BELOW |
| `dc-battery-room-01` | VRLA Battery Room H₂ Gas & Environment Monitor | TEMPERATURE · HUMIDITY · H2_PPM · VOC_INDEX | H₂ crit 10,000 ppm (10% LEL) · Temp crit 30°C · Humidity crit 70% |
| `dc-fire-suppression-01` | Clean Agent Fire Suppression Room Monitor | TEMPERATURE · SMOKE_PPM · CO2_PPM | Smoke warn 200 / crit 400 ppm · CO₂ crit 3000 ppm |
| `dc-diesel-tank-01` | Emergency Generator Diesel Fuel Tank Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX | Level crit 20% BELOW (fuel low) · Temp crit 50°C · VOC crit 300 |
| `dc-perimeter-01` | Data Center Physical Security & Environment Monitor | MOTION · TEMPERATURE · SOUND_DB · HUMIDITY | Temp crit 35°C · Sound crit 85 dB · Humidity crit 65% |
| `dc-airflow-01` | Hot/Cold Aisle Containment Airflow Monitor | TEMPERATURE · PRESSURE · HUMIDITY | Pressure crit 10 Pa BELOW (containment breach) · Temp crit 45°C |
| `dc-network-room-01` | Network Equipment Room Environment Monitor | TEMPERATURE · HUMIDITY | Temp warn 30°C / crit 35°C · Humidity warn 20% / crit 15% BELOW (static risk) |
| `dc-cooling-pump-01` | Chilled Water Cooling Loop Pump Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · CURRENT_A | Flow crit 500 LPM BELOW · Temp crit 18°C ABOVE (chilled water) · Current crit 60A |
| `dc-transfer-switch-01` | Automatic Transfer Switch (ATS) Power Monitor | VOLTAGE_V · CURRENT_A · TEMPERATURE | Voltage crit 195V BELOW · Current crit 1000A · Temp crit 70°C |
| `dc-cable-tray-01` | Cable Tray & Busway Thermal Monitor | TEMPERATURE · CURRENT_A · HUMIDITY | Temp warn 50°C / crit 70°C (hotspot) · Current crit 600A · Humidity crit 65% |
| `dc-raised-floor-01` | Raised Floor Plenum Airflow & Environment Monitor | TEMPERATURE · PRESSURE · HUMIDITY | Pressure crit 5 Pa BELOW · Temp crit 30°C · Humidity crit 60% |
| `dc-tape-library-01` | Tape Library & Archive Room Climate Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G | Temp warn 20°C / crit 25°C · Humidity warn 20% / crit 15% BELOW · Vib crit 1g |
| `dc-security-vault-01` | Secure Server Vault & Access Room Monitor | TEMPERATURE · HUMIDITY · MOTION · CO2_PPM | Temp crit 30°C · CO₂ crit 1200 ppm · Humidity crit 60% |
| `dc-cooling-tower-01` | Data Center Cooling Tower & Legionella Monitor | TEMPERATURE · WATER_LEVEL_PCT · PH · FLOW_LPM | Temp warn 45°C / crit 60°C (Legionella risk) · pH warn 8.5 / crit 9.0 ABOVE |
| `dc-fuel-storage-01` | Backup Generator Fuel Day Tank Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX · PRESSURE | Level warn 25% / crit 15% BELOW · VOC crit 350 (vapour) · Temp crit 50°C |

### 4. Agriculture & Greenhouse (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `agri-greenhouse-01` | Hydroponic Greenhouse Climate Controller | TEMPERATURE · HUMIDITY · CO2_PPM · LIGHT_LUX | CO₂ crit 1950 ppm · Humidity crit 94% |
| `agri-soil-01` | Smart Soil Multi-Parameter Sensor | TEMPERATURE · HUMIDITY · PH | pH warn 7.5 / crit 8.0 ABOVE |
| `agri-irrigation-01` | Smart Irrigation Flow & Pressure Monitor | FLOW_LPM · PRESSURE · WATER_LEVEL_PCT | Water level crit 8% BELOW · Pressure crit 580 kPa |
| `agri-weather-01` | Precision Agriculture Weather Station | TEMPERATURE · HUMIDITY · PRESSURE · UV_INDEX · LIGHT_LUX | Pressure crit 950 hPa BELOW (storm) · UV crit 10 |
| `agri-silo-01` | Grain Silo Condition Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp crit 35°C (hotspot) · CO₂ crit 3000 ppm |
| `agri-livestock-01` | Poultry House Environment Controller | TEMPERATURE · HUMIDITY · CO2_PPM · VOC_INDEX | Temp crit 34°C · CO₂ crit 3000 ppm (ammonia proxy) |
| `agri-aquaculture-01` | Fish Farm Aquaculture Pond Water Quality Monitor | TEMPERATURE · PH · DISSOLVED_O2 · WATER_LEVEL_PCT | O₂ crit 5 mg/L BELOW · pH warn 8.5 / crit 9.0 ABOVE · Temp crit 32°C |
| `agri-fertigation-01` | Smart Fertigation & Nutrient Dosing Monitor | FLOW_LPM · PRESSURE · PH · WATER_LEVEL_PCT | pH warn 7.5 / crit 8.0 ABOVE · Pressure crit 600 kPa · Level crit 8% BELOW |
| `agri-cold-store-01` | Post-Harvest Produce Cold Store Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn 3°C / crit 5°C ABOVE · CO₂ crit 4000 ppm |
| `agri-mushroom-farm-01` | Mushroom Cultivation Room Climate Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Humidity warn 92% / crit 96% ABOVE · CO₂ warn 3000 / crit 5000 ppm |
| `agri-beehive-01` | Smart Beehive Apiary Health Monitor | TEMPERATURE · HUMIDITY · SOUND_DB · VIBRATION_G | Temp warn 35°C / crit 37°C · Sound warn 65 / crit 80 dB (swarming signal) |
| `agri-vertical-farm-01` | Vertical Farm LED Growth Chamber Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · LIGHT_LUX | CO₂ warn 1200 / crit 2000 ppm · Light warn 15000 / crit 20000 lux |
| `agri-compost-01` | Compost Windrow Temperature & Maturity Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn 65°C / crit 75°C (self-ignition risk) · CO₂ crit 5000 ppm |
| `agri-hydro-nutrients-01` | Hydroponic Nutrient Solution Quality Monitor | TEMPERATURE · PH · DISSOLVED_O2 · FLOW_LPM | pH warn 6.5 / crit 7.0 ABOVE · O₂ crit 5 mg/L BELOW · Temp crit 25°C |
| `agri-cold-room-flower-01` | Cut Flower Cold Room Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · LIGHT_LUX | Temp warn 3°C / crit 5°C ABOVE · Humidity crit 92% · Light crit 100 lux |
| `agri-pig-barn-01` | Pig Barn Environment & Welfare Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · VOC_INDEX | Temp crit 30°C · CO₂ crit 3000 ppm · VOC crit 400 (ammonia proxy) |
| `agri-cattle-barn-01` | Dairy Cattle Barn Heat Stress Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn 24°C / crit 27°C (heat stress index) · CO₂ crit 3000 ppm |
| `agri-pump-house-01` | Irrigation Pump House Health Monitor | CURRENT_A · VIBRATION_G · PRESSURE · TEMPERATURE | Vib warn 3g / crit 5g · Current crit 45A · Pressure crit 700 kPa |
| `agri-solar-pump-01` | Solar-Powered Irrigation Pump Monitor | VOLTAGE_V · CURRENT_A · FLOW_LPM · WATER_LEVEL_PCT | Voltage crit 30V BELOW · Flow crit 50 LPM BELOW · Level crit 5% BELOW |
| `agri-fish-hatchery-01` | Fish Hatchery Incubation Water Quality Monitor | TEMPERATURE · PH · DISSOLVED_O2 · WATER_LEVEL_PCT | Temp warn 26°C / crit 30°C · O₂ crit 6 mg/L BELOW · pH warn 7.5 / crit 8.0 ABOVE |

### 5. Healthcare & Pharmaceuticals (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `health-vaccine-fridge-01` | WHO-PQS Vaccine Storage (+2 to +8°C) | TEMPERATURE · HUMIDITY | Temp warn 7°C / crit 8°C (cold chain breach) |
| `health-clean-room-01` | ISO Class 7 Clean Room Monitor | TEMPERATURE · HUMIDITY · PRESSURE · PM25 · PM10 | PM2.5 crit 10 µg/m³ · Temp crit 23°C |
| `health-autoclave-01` | Steam Autoclave — 134°C Cycle | TEMPERATURE · PRESSURE | Temp crit 138°C · Pressure crit 340 kPa |
| `health-lab-gas-01` | Laboratory Chemical Fume & Gas Safety | CO_PPM · O3_PPB · VOC_INDEX · CO2_PPM | CO crit 50 ppm · O₃ crit 100 ppb |
| `health-operating-room-01` | Operating Theatre HVAC & Air Quality | TEMPERATURE · HUMIDITY · PRESSURE · PM25 · CO2_PPM | PM2.5 crit 5 µg/m³ · CO₂ crit 1000 ppm |
| `health-mri-room-01` | MRI Cryogen Quench & O₂ Deficiency Monitor | TEMPERATURE · O2_PCT · PRESSURE | O₂ crit 19.5% BELOW (asphyxiation risk) · Pressure crit 115 kPa ABOVE (helium quench) |
| `health-pharmacy-01` | Hospital Pharmacy Drug Storage Monitor | TEMPERATURE · HUMIDITY · LIGHT_LUX | Temp warn 23°C / crit 25°C · Humidity crit 60% · Light crit 500 lux (photodegradation) |
| `health-eto-sterilizer-01` | EtO Gas Sterilizer Emission Safety Monitor | VOC_INDEX · CO_PPM · TEMPERATURE | VOC crit 350 (EtO proxy) · CO crit 50 ppm · Temp crit 60°C |
| `health-blood-bank-01` | Blood Bank & Plasma Freezer Monitor | TEMPERATURE · HUMIDITY | Temp warn 5°C / crit 6°C ABOVE (RBC breach) · Humidity crit 70% |
| `health-neonatal-01` | NICU Environment & Air Quality Monitor | TEMPERATURE · HUMIDITY · PRESSURE · CO2_PPM | Temp crit 24°C ABOVE · CO₂ crit 800 ppm · Pressure crit 5 Pa BELOW |
| `health-isolation-room-01` | Negative Pressure Isolation Room Monitor | TEMPERATURE · HUMIDITY · PRESSURE · PM25 | Pressure crit -5 Pa ABOVE (negative pressure lost) · PM2.5 crit 5 µg/m³ |
| `health-dialysis-01` | Haemodialysis Water Treatment Monitor | TEMPERATURE · FLOW_LPM · PRESSURE · PH | Temp crit 25°C ABOVE · Flow crit 10 LPM BELOW · pH warn 7.5 / crit 8.0 ABOVE |
| `health-gas-manifold-01` | Medical Gas Manifold & Pipeline Monitor | PRESSURE · FLOW_LPM · TEMPERATURE | Pressure warn 350 / crit 300 kPa BELOW (O₂ supply) · Flow crit 50 LPM BELOW |
| `health-morgue-01` | Mortuary & Pathology Cold Room Monitor | TEMPERATURE · HUMIDITY | Temp warn 3°C / crit 5°C ABOVE · Humidity crit 70% |
| `health-radiology-01` | Radiology & X-Ray Room Environment Monitor | TEMPERATURE · HUMIDITY · PRESSURE | Temp crit 25°C · Humidity crit 60% · Pressure crit 5 Pa BELOW |
| `health-lab-incubator-01` | Laboratory CO₂ Cell Culture Incubator Monitor | TEMPERATURE · CO2_PPM · HUMIDITY | Temp warn 36.8°C / crit 37.5°C · CO₂ warn 40000 / crit 60000 ppm (5–6%) · Humidity crit 95% ABOVE |
| `health-liquid-nitrogen-01` | Biological Sample Liquid Nitrogen Dewar Monitor | WATER_LEVEL_PCT · TEMPERATURE · O2_PCT | Level crit 20% BELOW · O₂ crit 19.5% BELOW (asphyxiation) · Temp crit -150°C ABOVE |
| `health-hvac-hospital-01` | Hospital Central HVAC Air Handling Unit Monitor | TEMPERATURE · HUMIDITY · PRESSURE · PM25 | PM2.5 crit 10 µg/m³ · Temp crit 22°C · Humidity crit 65% |
| `health-dental-01` | Dental Surgery Compressed Air & Gas Safety Monitor | PRESSURE · CO_PPM · VOC_INDEX · TEMPERATURE | Pressure crit 400 kPa BELOW · CO crit 25 ppm · VOC crit 200 |
| `health-waste-01` | Clinical Waste Refrigerated Storage Monitor | TEMPERATURE · HUMIDITY · CO_PPM · VOC_INDEX | Temp crit 8°C ABOVE · CO crit 25 ppm · VOC crit 300 |

### 6. Energy & Utilities (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `energy-solar-inverter-01` | Grid-Tied Solar PV Inverter — 10 kW | TEMPERATURE · VOLTAGE_V · CURRENT_A · POWER_W · ENERGY_KWH | Temp crit 78°C · Voltage crit 995V |
| `energy-smart-meter-01` | Industrial 3-Phase Smart Energy Meter | POWER_W · CURRENT_A · VOLTAGE_V · ENERGY_KWH | Power crit 59 kW · Current crit 148A |
| `energy-transformer-01` | Distribution Transformer — 33kV/11kV | TEMPERATURE · CURRENT_A · VIBRATION_G | Temp warn 80°C / crit 90°C · Vib crit 4g |
| `energy-water-plant-01` | Water Treatment Plant Process Monitor | PH · FLOW_LPM · WATER_LEVEL_PCT · TEMPERATURE | pH crit 9.0 ABOVE · Level crit 8% BELOW |
| `energy-wind-turbine-01` | Wind Turbine Drivetrain Health — 2 MW | TEMPERATURE · VIBRATION_G · SOUND_DB · CURRENT_A | Vib crit 8g · Sound crit 100 dB |
| `energy-bess-01` | Grid-Scale Battery Energy Storage System (BESS) Monitor | TEMPERATURE · VOLTAGE_V · CURRENT_A · BATTERY_PCT | Temp warn 40°C / crit 55°C (thermal runaway risk) · Battery crit 5% BELOW |
| `energy-hydro-01` | Hydroelectric Turbine & Dam Level Monitor | VIBRATION_G · WATER_LEVEL_PCT · TEMPERATURE · CURRENT_A | Vib warn 3g / crit 6g · Level crit 95% ABOVE (spillway risk) |
| `energy-gas-turbine-01` | Gas Turbine Power Plant Health Monitor | TEMPERATURE · VIBRATION_G · PRESSURE · CURRENT_A | Temp crit 650°C (exhaust) · Vib crit 5g · Pressure crit 2500 kPa |
| `energy-substation-01` | HV/MV Electrical Substation Environment Monitor | TEMPERATURE · HUMIDITY · CURRENT_A · VOLTAGE_V | Temp warn 55°C / crit 65°C (transformer oil) · Humidity crit 80% |
| `energy-geothermal-01` | Geothermal Well & Binary Power Plant Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · H2S_PPM | Temp crit 180°C · Pressure crit 5000 kPa · H₂S crit 10 ppm |
| `energy-tidal-01` | Tidal / Wave Energy Converter Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · WATER_LEVEL_PCT | Vib crit 8g · Current crit 200A · Level crit 95% ABOVE |
| `energy-grid-inverter-01` | Grid-Scale Power Inverter & Converter Monitor | TEMPERATURE · VOLTAGE_V · CURRENT_A · POWER_W | Temp crit 70°C · Voltage crit 690V ABOVE · Power crit 500 kW |
| `energy-fuel-cell-01` | Hydrogen Fuel Cell Power Plant Monitor | TEMPERATURE · PRESSURE · CURRENT_A · H2_PPM | H₂ crit 10,000 ppm · Temp crit 85°C (stack) · Pressure crit 300 kPa |
| `energy-biogas-01` | Biogas Anaerobic Digester & CHP Monitor | TEMPERATURE · PRESSURE · CH4_PPM · CO2_PPM | Temp warn 35°C / crit 55°C · CH₄ crit 300,000 ppm BELOW (low yield) |
| `energy-cooling-pond-01` | Power Plant Cooling Pond & Water Intake Monitor | TEMPERATURE · WATER_LEVEL_PCT · FLOW_LPM · PH | Temp crit 35°C (thermal discharge limit) · Level crit 10% BELOW |
| `energy-switchyard-01` | Electrical Switchyard & Circuit Breaker Monitor | TEMPERATURE · HUMIDITY · CURRENT_A · VIBRATION_G | Temp warn 65°C / crit 80°C · Vib crit 3g · Humidity crit 85% |
| `energy-oil-transformer-01` | Oil-Filled Power Transformer Health Monitor | TEMPERATURE · CURRENT_A · VIBRATION_G · PRESSURE | Temp warn 85°C / crit 95°C · Vib crit 3g · Pressure crit 50 kPa (conservator) |
| `energy-cable-tunnel-01` | Underground HV Cable Tunnel Monitor | TEMPERATURE · HUMIDITY · CO_PPM · WATER_LEVEL_PCT | Temp warn 60°C / crit 70°C (cable rating) · Water level crit 30% ABOVE · CO crit 25 ppm |
| `energy-solar-farm-01` | Utility-Scale Solar Farm Array Performance Monitor | TEMPERATURE · VOLTAGE_V · CURRENT_A · POWER_W | Temp crit 75°C (module) · Power warn 50 kW BELOW · Voltage crit 1500V ABOVE |
| `energy-biomass-01` | Biomass / Biogas Power Plant Combustion Monitor | TEMPERATURE · CO_PPM · CH4_PPM · FLOW_LPM | Temp crit 900°C (combustion) · CO crit 100 ppm · CH₄ crit 20% LEL |

### 7. Smart Building & Facilities (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `building-hvac-01` | Central HVAC AHU — Demand Control Ventilation | TEMPERATURE · HUMIDITY · CO2_PPM · PRESSURE | CO₂ crit 1500 ppm · Temp crit 28°C |
| `building-fire-alarm-01` | Addressable Fire Safety — Heat + Smoke + CO | SMOKE_PPM · CO_PPM · TEMPERATURE | Smoke warn 300 / crit 600 ppm · CO crit 100 ppm |
| `building-occupancy-01` | Smart Occupancy & Comfort Sensor | MOTION · CO2_PPM · LIGHT_LUX · SOUND_DB | CO₂ crit 1800 ppm · Sound crit 90 dB |
| `building-elevator-01` | Elevator Machine Room — Overheating Prevention | TEMPERATURE · VIBRATION_G · CURRENT_A | Temp warn 40°C / crit 50°C |
| `building-parking-01` | Basement Car Park Air Quality — CO Management | CO_PPM · CO2_PPM · PM25 | CO warn 25 / crit 50 ppm (EN 50545-1) |
| `building-water-tank-01` | Roof Water Tank — Level & Legionella Risk | WATER_LEVEL_PCT · TEMPERATURE · PRESSURE | Temp crit 65°C (Legionella zone) · Level crit 10% BELOW |
| `building-ev-charger-01` | EV Fleet Charging Station Health Monitor | CURRENT_A · VOLTAGE_V · POWER_W · TEMPERATURE | Current crit 80A · Power crit 22 kW · Temp crit 60°C |
| `building-solar-01` | Rooftop Solar PV Array Performance Monitor | TEMPERATURE · VOLTAGE_V · CURRENT_A · POWER_W | Temp crit 75°C (module) · Voltage crit 900V ABOVE · Power warn 500W BELOW |
| `building-swimming-pool-01` | Commercial Swimming Pool Water Quality Monitor | TEMPERATURE · PH · WATER_LEVEL_PCT | Temp warn 29°C / crit 31°C · pH warn 8.0 / crit 8.5 ABOVE |
| `building-kitchen-01` | Commercial Kitchen Ventilation & Gas Safety Monitor | TEMPERATURE · CO_PPM · SMOKE_PPM · CO2_PPM | CO warn 25 / crit 50 ppm · Smoke crit 500 ppm · Temp crit 350°C (duct fire) |
| `building-chiller-01` | Central Chiller Plant & Heat Pump Monitor | TEMPERATURE · PRESSURE · CURRENT_A · FLOW_LPM | Temp crit 12°C ABOVE (chilled water supply) · Current crit 300A · Pressure crit 1200 kPa |
| `building-ahu-filter-01` | AHU Filter Differential Pressure Monitor | PRESSURE · TEMPERATURE · HUMIDITY | Pressure crit 250 Pa ABOVE (filter clog) · Temp crit 30°C · Humidity crit 70% |
| `building-lighting-01` | Smart Lighting Energy & Occupancy Monitor | LIGHT_LUX · MOTION · CURRENT_A · POWER_W | Current crit 20A · Power crit 5 kW · Light crit 20 lux BELOW (insufficient) |
| `building-escalator-01` | Escalator & Moving Walkway Health Monitor | CURRENT_A · VIBRATION_G · TEMPERATURE · SOUND_DB | Current crit 60A · Vib warn 2g / crit 4g · Sound crit 80 dB · Temp crit 70°C |
| `building-facade-01` | Building Facade & Curtain Wall Structural Monitor | VIBRATION_G · TILT_DEG · TEMPERATURE · HUMIDITY | Vib crit 3g · Tilt warn 1° / crit 2° ABOVE · Humidity crit 95% |
| `building-sprinkler-01` | Fire Sprinkler System Pressure & Water Flow Monitor | PRESSURE · FLOW_LPM · TEMPERATURE · WATER_LEVEL_PCT | Pressure crit 400 kPa BELOW · Level crit 10% BELOW |
| `building-basement-flood-01` | Basement & Underground Car Park Flood Monitor | WATER_LEVEL_PCT · CO_PPM · CO2_PPM · MOTION | Level warn 5% / crit 20% ABOVE · CO crit 35 ppm |
| `building-rooftop-01` | Rooftop Equipment & Wind Load Monitor | TEMPERATURE · HUMIDITY · PRESSURE · VIBRATION_G | Pressure crit 950 hPa BELOW (storm) · Vib crit 5g (wind load) · Temp crit 60°C |
| `building-transformer-01` | Building HV/MV Transformer & Switchroom Monitor | TEMPERATURE · HUMIDITY · CURRENT_A · VOLTAGE_V | Temp warn 55°C / crit 65°C · Humidity crit 75% · Current crit 400A |
| `building-waste-room-01` | Waste Collection & Compactor Room Monitor | TEMPERATURE · CO_PPM · VOC_INDEX · MOTION | Temp crit 38°C · CO crit 25 ppm · VOC crit 350 |

### 8. Logistics & Warehouse (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `logistics-cold-truck-01` | Cold Chain Refrigerated Transport Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G | Temp warn 3°C / crit 6°C · Vib crit 7g |
| `logistics-forklift-01` | Electric Forklift Battery & Health Monitor | BATTERY_PCT · BATTERY_V · VIBRATION_G · TEMPERATURE | Battery crit 10% BELOW · Temp crit 65°C |
| `logistics-warehouse-air-01` | High-Bay Warehouse Air Quality — Forklift CO | CO2_PPM · CO_PPM · VOC_INDEX · TEMPERATURE | CO warn 20 / crit 35 ppm · CO₂ crit 2500 ppm |
| `logistics-loading-dock-01` | Loading Dock Environmental & Security Monitor | TEMPERATURE · HUMIDITY · CO_PPM · MOTION | CO crit 35 ppm · Humidity crit 92% |
| `logistics-racking-01` | Automated Storage Racking Structural Integrity | VIBRATION_G · TILT_DEG · TEMPERATURE | Vib crit 2.5g · Tilt warn 3° / crit 5° ABOVE |
| `logistics-agv-01` | Autonomous Guided Vehicle (AGV) Fleet Health Monitor | BATTERY_PCT · VIBRATION_G · TEMPERATURE · CURRENT_A | Battery crit 10% BELOW · Temp crit 60°C · Vib crit 4g |
| `logistics-hazmat-01` | Hazardous Material Storage Bay Safety Monitor | TEMPERATURE · HUMIDITY · CO_PPM · VOC_INDEX | Temp crit 35°C · CO crit 35 ppm · VOC crit 400 |
| `logistics-refrigerated-dc-01` | Refrigerated Distribution Center Ambient Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | Temp warn 3°C / crit 6°C · CO₂ crit 3000 ppm |
| `logistics-dock-door-01` | Loading Dock Door Seal & Temperature Bridge Monitor | TEMPERATURE · HUMIDITY · MOTION | Temp crit 15°C · Humidity crit 85% |
| `logistics-sorter-01` | Automated Parcel Sorter Health Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 2g / crit 4g · Current crit 150A · Sound crit 90 dB |
| `logistics-conveyor-scan-01` | Conveyor & Barcode Scanner Line Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · MOTION | Vib warn 2g / crit 3g · Temp crit 55°C · Current crit 30A |
| `logistics-compressed-air-01` | Pneumatic Sortation Compressed Air Monitor | PRESSURE · FLOW_LPM · HUMIDITY · TEMPERATURE | Pressure crit 600 kPa BELOW · Humidity crit 45% (instrument air dew point) |
| `logistics-yard-01` | Logistics Yard Environmental & Security Monitor | TEMPERATURE · HUMIDITY · MOTION · CO_PPM | CO crit 35 ppm (vehicle exhaust) · Temp crit 40°C |
| `logistics-battery-charging-01` | EV & Forklift Battery Charging Bay Monitor | TEMPERATURE · H2_PPM · CURRENT_A · VOLTAGE_V | H₂ crit 10,000 ppm (charging off-gas) · Temp crit 45°C · Current crit 200A |
| `logistics-cold-store-door-01` | Cold Store Blast Freezer Door Seal Monitor | TEMPERATURE · HUMIDITY · MOTION | Temp crit -10°C ABOVE (seal breach) · Humidity crit 85% |
| `logistics-hazmat-container-01` | Intermodal Hazmat Container Monitor | TEMPERATURE · HUMIDITY · CO_PPM · VOC_INDEX | Temp crit 40°C · CO crit 35 ppm · VOC crit 400 |
| `logistics-roof-sensor-01` | Warehouse Roof Structural Load & Leak Monitor | VIBRATION_G · WATER_LEVEL_PCT · TEMPERATURE · HUMIDITY | Vib crit 3g (impact) · Water level warn 5% / crit 15% ABOVE (roof leak) |
| `logistics-fire-suppression-01` | Warehouse Fire Suppression System Monitor | TEMPERATURE · SMOKE_PPM · PRESSURE · WATER_LEVEL_PCT | Smoke warn 250 / crit 500 ppm · Pressure crit 600 kPa BELOW |
| `logistics-uld-01` | Aviation ULD / Air Cargo Container Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G · PRESSURE | Temp warn 15°C / crit 25°C · Vib crit 5g · Pressure crit 75 kPa BELOW |
| `logistics-parcel-locker-01` | Smart Parcel Locker & Last-Mile Hub Monitor | TEMPERATURE · HUMIDITY · MOTION · CURRENT_A | Temp warn 35°C / crit 45°C · Humidity crit 80% · Current crit 20A |

### 9. Oil & Gas (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `og-pipeline-leak-01` | Pipeline Leak Detector — Pressure Drop & Flow Anomaly | PRESSURE · FLOW_LPM · TEMPERATURE · VIBRATION_G · CO_PPM | Pressure crit 500 kPa BELOW · CO crit 25 ppm · Vib crit 5g (acoustic anomaly) |
| `og-wellhead-01` | Wellhead Health Monitor — Pressure, Flow & Integrity | PRESSURE · TEMPERATURE · FLOW_LPM · VIBRATION_G · H2S_PPM | Pressure crit 5000 kPa ABOVE (blowout risk) · H₂S warn 1 / crit 10 ppm |
| `og-gas-detector-01` | Area Combustible Gas & Toxic Gas Safety Detector | CO_PPM · H2S_PPM · VOC_INDEX · CO2_PPM | CO crit 50 ppm · H₂S warn 1 / crit 10 ppm · VOC crit 500 (LEL proxy) |
| `og-separator-01` | Three-Phase Oil–Water–Gas Separator Process Monitor | PRESSURE · TEMPERATURE · FLOW_LPM · WATER_LEVEL_PCT | Pressure crit 800 kPa · Temp crit 80°C · Level crit 95% ABOVE |
| `og-storage-tank-01` | Crude Oil Storage Tank Level & Vapour Monitor | WATER_LEVEL_PCT · TEMPERATURE · PRESSURE · VOC_INDEX | Level warn 90% / crit 95% ABOVE · Temp crit 45°C · VOC crit 400 (LEL proxy) |
| `og-flare-stack-01` | Flare Stack Combustion Efficiency & Emissions Monitor | TEMPERATURE · CO_PPM · VOC_INDEX · SMOKE_PPM | Temp warn 800 / crit 1000°C · CO crit 100 ppm |
| `og-compressor-01` | Gas Compressor Station Health Monitor | TEMPERATURE · PRESSURE · VIBRATION_G · CO_PPM · H2S_PPM | Pressure crit 7000 kPa · Temp crit 120°C · Vib crit 7g · H₂S crit 10 ppm |
| `og-pump-jack-01` | Pump Jack (Nodding Donkey) Artificial Lift Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE | Vib warn 4g / crit 7g · Current crit 60A · Temp crit 90°C |
| `og-cathodic-01` | Pipeline Cathodic Protection (CP) Monitor | VOLTAGE_V · CURRENT_A · TEMPERATURE | Voltage crit -850 mV ABOVE (inadequate protection) · Temp crit 65°C |
| `og-tanker-loading-01` | Tanker Ship Loading Arm & Marine Jetty Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · VOC_INDEX | Flow crit 2000 LPM ABOVE · VOC crit 400 · Pressure crit 1200 kPa |
| `og-subsea-01` | Subsea Wellhead & Production Riser Integrity Monitor | PRESSURE · TEMPERATURE · VIBRATION_G | Pressure warn 4000 / crit 5000 kPa ABOVE · Temp crit 130°C |
| `og-separator-boot-01` | Free Water Knockout (FWKO) & Boot Separator Monitor | PRESSURE · TEMPERATURE · WATER_LEVEL_PCT · FLOW_LPM | Level warn 80% / crit 90% ABOVE · Pressure crit 500 kPa |
| `og-metering-station-01` | Fiscal Metering Station & Custody Transfer Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · VIBRATION_G | Pressure crit 1500 kPa · Vib crit 3g · Temp crit 60°C |
| `og-pig-launcher-01` | Pipeline Pig Launcher & Receiver Monitor | PRESSURE · TEMPERATURE · VIBRATION_G · CO_PPM | Pressure warn 600 / crit 800 kPa · CO crit 25 ppm |
| `og-gas-processing-01` | Natural Gas Processing & Dehydration Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · H2S_PPM | Temp crit 65°C · Pressure crit 8000 kPa · H₂S crit 10 ppm |
| `og-loading-terminal-01` | Petroleum Product Loading Terminal Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · VOC_INDEX | Flow crit 5000 LPM ABOVE · VOC crit 400 (LEL proxy) · Pressure crit 1000 kPa |
| `og-drill-rig-01` | Onshore Drill Rig & Derrick Health Monitor | VIBRATION_G · PRESSURE · TEMPERATURE · H2S_PPM | Vib warn 5g / crit 10g · H₂S warn 1 / crit 10 ppm · Pressure crit 3000 kPa |
| `og-offshore-platform-01` | Offshore Platform Structural & Weather Monitor | VIBRATION_G · PRESSURE · HUMIDITY · TEMPERATURE | Vib crit 6g · Pressure crit 950 hPa BELOW (storm) · Humidity crit 98% |
| `og-lifeboat-station-01` | Offshore Lifeboat Station & Muster Area Monitor | CO_PPM · CO2_PPM · TEMPERATURE · O2_PCT | CO crit 25 ppm · O₂ crit 19.5% BELOW · CO₂ crit 2000 ppm |
| `og-hvac-platform-01` | Offshore Platform HVAC & Gas Ingress Monitor | CO_PPM · H2S_PPM · VOC_INDEX · TEMPERATURE | CO crit 25 ppm · H₂S crit 1 ppm · VOC crit 400 |

### 10. Mining (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `mine-air-quality-01` | Underground Mine Air Quality & Gas Safety Station | CO_PPM · CO2_PPM · CH4_PPM · O2_PCT · PM25 | CO crit 35 ppm (MSHA) · CH₄ warn 10,000 / crit 20,000 ppm · O₂ crit 19.5% BELOW |
| `mine-blast-monitor-01` | Seismic & Blast Vibration Monitor | VIBRATION_G · SOUND_DB · TILT_DEG | Vib warn 5g / crit 10g · Sound crit 140 dB · Tilt crit 3° ABOVE |
| `mine-water-pump-01` | Mine Dewatering Pump Health Monitor | FLOW_LPM · WATER_LEVEL_PCT · PRESSURE · CURRENT_A · TEMPERATURE | Level crit 85% ABOVE · Temp crit 70°C · Current crit 120A |
| `mine-equipment-01` | Heavy Mining Equipment (Haul Truck) Health Monitor | TEMPERATURE · VIBRATION_G · CURRENT_A | Temp crit 110°C · Vib crit 8g · Current crit 300A |
| `mine-conveyor-01` | Mining Conveyor Belt Structural Health Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · MOTION | Vib warn 3g / crit 5g · Temp crit 80°C · Current crit 200A |
| `mine-structural-01` | Shaft & Tunnel Structural Integrity Monitor | VIBRATION_G · TILT_DEG · PRESSURE | Vib crit 3g · Tilt warn 2° / crit 4° ABOVE · Pressure crit 500 kPa ABOVE |
| `mine-refuge-chamber-01` | Underground Refuge Chamber Life Support Monitor | O2_PCT · CO2_PPM · CO_PPM · TEMPERATURE · WATER_LEVEL_PCT | O₂ crit 19.5% BELOW · CO crit 25 ppm · CO₂ crit 3000 ppm |
| `mine-hoist-01` | Mine Shaft Winder & Cage Hoist Health Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 4g / crit 8g · Temp crit 95°C · Sound crit 100 dB |
| `mine-crusher-01` | Primary Rock Crusher Health Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · SOUND_DB | Vib warn 5g / crit 9g · Temp crit 90°C · Sound crit 120 dB |
| `mine-tailings-dam-01` | Tailings Storage Facility (TSF) Structural Monitor | WATER_LEVEL_PCT · PRESSURE · TILT_DEG · VIBRATION_G | Level warn 85% / crit 92% ABOVE · Tilt warn 1° / crit 2° ABOVE |
| `mine-ventilation-01` | Main Mine Ventilation Fan Health Monitor | CURRENT_A · VIBRATION_G · TEMPERATURE · PRESSURE | Current crit 250A · Vib warn 3g / crit 6g · Pressure crit 1500 Pa BELOW (airflow loss) |
| `mine-explosive-store-01` | Explosive Magazine Storage Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G · MOTION | Temp warn 35°C / crit 40°C · Humidity crit 70% · Vib crit 1g (detonation risk) |
| `mine-skip-hoist-01` | Mine Skip Hoist & Headframe Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 5g / crit 9g · Current crit 500A · Sound crit 105 dB |
| `mine-dewatering-sump-01` | Underground Dewatering Sump & Pump Monitor | WATER_LEVEL_PCT · FLOW_LPM · CURRENT_A · TEMPERATURE | Level warn 75% / crit 88% ABOVE · Current crit 90A · Temp crit 70°C |
| `mine-ball-mill-01` | Ball Mill & Grinding Circuit Health Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · SOUND_DB | Vib warn 4g / crit 8g · Temp crit 85°C · Current crit 400A · Sound crit 110 dB |
| `mine-leach-pad-01` | Heap Leach Pad & Solution Monitor | PH · FLOW_LPM · WATER_LEVEL_PCT · TEMPERATURE | pH warn 2.5 / crit 2.0 BELOW · Level warn 85% / crit 93% ABOVE |
| `mine-diesel-bay-01` | Underground Diesel Refuelling Bay Safety Monitor | CO_PPM · CO2_PPM · TEMPERATURE · VOC_INDEX | CO warn 25 / crit 50 ppm · CO₂ crit 3000 ppm · VOC crit 350 |
| `mine-flotation-01` | Froth Flotation Cell Process Monitor | PH · FLOW_LPM · TEMPERATURE · VIBRATION_G | pH warn 7.0 / crit 8.0 ABOVE · Temp crit 45°C · Vib crit 4g |
| `mine-emergency-monitor-01` | Mine Emergency Gas & Safety System Monitor | CO_PPM · CH4_PPM · O2_PCT · VIBRATION_G | CO crit 35 ppm · CH₄ crit 10,000 ppm · O₂ crit 19.5% BELOW |
| `mine-surface-water-01` | Open Cut Mine Pit Wall Water Management Monitor | WATER_LEVEL_PCT · PRESSURE · TILT_DEG · VIBRATION_G | Level warn 70% / crit 85% ABOVE · Tilt warn 2° / crit 3° ABOVE (slope failure risk) |

### 11. Water & Wastewater Treatment (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `water-pump-station-01` | Raw Water Intake Pump Station Monitor | FLOW_LPM · PRESSURE · CURRENT_A · VIBRATION_G | Flow crit 5000 LPM BELOW · Pressure crit 400 kPa · Vib crit 5g |
| `water-treatment-01` | Water Treatment Chemical Dosing Monitor | PH · FLOW_LPM · PRESSURE · TEMPERATURE | pH warn 7.8 / crit 8.5 ABOVE · Flow crit 10 LPM BELOW (dosing failure) |
| `water-chlorination-01` | Chlorination Station Cl₂ Gas Safety Monitor | CO_PPM · VOC_INDEX · PRESSURE · TEMPERATURE | VOC crit 400 (Cl₂ proxy) · CO crit 25 ppm · Pressure crit 800 kPa |
| `water-reservoir-01` | Elevated Water Reservoir Level & Quality Monitor | WATER_LEVEL_PCT · PH · TEMPERATURE · PRESSURE | Level crit 15% BELOW (supply loss) · pH crit 9.0 ABOVE |
| `water-sewer-01` | Sewer Lift Station & H₂S Safety Monitor | WATER_LEVEL_PCT · CO_PPM · H2S_PPM · VIBRATION_G | Level crit 85% ABOVE · H₂S warn 5 / crit 20 ppm · CO crit 35 ppm |
| `water-desalination-01` | Reverse Osmosis Desalination Plant Monitor | PRESSURE · FLOW_LPM · TEMPERATURE · CURRENT_A | Pressure crit 6000 kPa · Temp crit 45°C · Flow crit 500 LPM BELOW |
| `water-aeration-01` | Wastewater Aeration Basin Monitor | DISSOLVED_O2 · TEMPERATURE · PH · FLOW_LPM | O₂ warn 2 / crit 1 mg/L BELOW · pH warn 8.0 / crit 9.0 ABOVE · Temp crit 35°C |
| `water-clarifier-01` | Primary & Secondary Clarifier Monitor | WATER_LEVEL_PCT · VIBRATION_G · CURRENT_A · TEMPERATURE | Level warn 80% / crit 90% ABOVE · Vib crit 3g (scraper) · Current crit 30A |
| `water-uv-disinfection-01` | UV Disinfection Reactor Monitor | CURRENT_A · FLOW_LPM · TEMPERATURE · PRESSURE | Current crit 5A BELOW (UV lamp failure) · Flow crit 1000 LPM ABOVE · Temp crit 40°C |
| `water-sludge-digester-01` | Anaerobic Sludge Digester Monitor | TEMPERATURE · PRESSURE · CH4_PPM · CO2_PPM | Temp warn 35°C / crit 37°C (mesophilic) · CH₄ crit 300,000 ppm BELOW (low yield) · Pressure crit 30 kPa |
| `water-chemical-dosing-01` | Coagulant & Flocculant Chemical Dosing Monitor | FLOW_LPM · PRESSURE · WATER_LEVEL_PCT · TEMPERATURE | Level crit 10% BELOW (chemical low) · Flow crit 0.5 LPM BELOW |
| `water-pressure-zone-01` | Distribution Network Pressure Zone Monitor | PRESSURE · FLOW_LPM · TEMPERATURE | Pressure warn 200 / crit 150 kPa BELOW (burst/leak) · Flow crit 5000 LPM ABOVE |
| `water-stormwater-01` | Stormwater Retention Basin & Overflow Monitor | WATER_LEVEL_PCT · FLOW_LPM · PRESSURE | Level warn 85% / crit 92% ABOVE · Flow crit 10000 LPM ABOVE (overflow) |
| `water-membrane-bioreactor-01` | Membrane Bioreactor (MBR) Process Monitor | PRESSURE · FLOW_LPM · TEMPERATURE · DISSOLVED_O2 | Pressure crit 35 kPa ABOVE (membrane fouling) · O₂ crit 1.5 mg/L BELOW |
| `water-odour-control-01` | Biofilter Odour Control & H₂S Monitor | H2S_PPM · CO_PPM · VOC_INDEX · TEMPERATURE | H₂S warn 5 / crit 20 ppm · VOC crit 300 |
| `water-groundwater-01` | Groundwater Bore & Aquifer Level Monitor | WATER_LEVEL_PCT · TEMPERATURE · PH · PRESSURE | Level warn 30% / crit 20% BELOW · pH warn 8.5 / crit 9.0 ABOVE |
| `water-intake-screen-01` | Water Intake Drum Screen & Trash Rack Monitor | PRESSURE · VIBRATION_G · CURRENT_A · FLOW_LPM | Pressure crit 50 kPa ABOVE (screen clog) · Vib crit 4g · Current crit 25A |
| `water-sludge-press-01` | Belt Filter Press & Sludge Dewatering Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · PRESSURE | Vib warn 3g / crit 5g · Current crit 40A · Pressure crit 700 kPa |
| `water-effluent-01` | Final Effluent Quality & Discharge Monitor | PH · TEMPERATURE · DISSOLVED_O2 · FLOW_LPM | pH warn 9.0 / crit 10.0 ABOVE · O₂ crit 5 mg/L BELOW · Temp crit 35°C (thermal discharge limit) |
| `water-sludge-dryer-01` | Sludge Dewatering & Thermal Dryer Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G · CURRENT_A | Temp crit 200°C · Humidity crit 90% · Vib crit 5g · Current crit 150A |

### 12. Chemical & Petrochemical (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `chem-reactor-01` | Chemical Batch Reactor Process Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · PH | Pressure warn 1200 / crit 1500 kPa · Temp crit 180°C · pH warn 4.0 / crit 3.0 BELOW |
| `chem-storage-tank-01` | Bulk Chemical Storage Tank Safety Monitor | WATER_LEVEL_PCT · TEMPERATURE · PRESSURE · VOC_INDEX | Level warn 90% / crit 95% ABOVE · VOC crit 450 (LEL proxy) · Temp crit 50°C |
| `chem-scrubber-01` | Industrial Exhaust Gas Scrubber Monitor | PRESSURE · FLOW_LPM · TEMPERATURE · CO_PPM · VOC_INDEX | Pressure crit 300 kPa · CO crit 50 ppm · VOC crit 400 |
| `chem-pump-01` | Chemical Transfer Pump Health Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · PRESSURE | Vib warn 3g / crit 6g · Temp crit 85°C · Current crit 45A |
| `chem-gas-safety-01` | Chemical Plant Multi-Gas Area Safety Detector | CO_PPM · H2S_PPM · CO2_PPM · VOC_INDEX · O2_PCT | CO crit 50 ppm · H₂S crit 10 ppm · O₂ crit 19.5% BELOW · VOC crit 500 |
| `chem-distillation-01` | Distillation Column Process Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · VIBRATION_G | Pressure crit 1800 kPa · Temp warn 200 / crit 250°C · Vib crit 4g |
| `chem-heat-exchanger-01` | Shell & Tube Heat Exchanger Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · VIBRATION_G | Temp crit 200°C · Pressure crit 2000 kPa · Vib crit 4g |
| `chem-centrifuge-01` | Industrial Centrifuge Health Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · SOUND_DB | Vib warn 4g / crit 8g · Temp crit 70°C · Sound crit 100 dB |
| `chem-crystalliser-01` | Chemical Crystallisation Vessel Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · PH | Temp warn 60°C / crit 80°C · pH warn 6.0 / crit 5.0 BELOW |
| `chem-cooling-system-01` | Plant-Wide Cooling Water Circuit Monitor | TEMPERATURE · FLOW_LPM · PRESSURE · PH | Temp crit 38°C · pH warn 8.5 / crit 9.0 ABOVE · Flow crit 2000 LPM BELOW |
| `chem-toxic-gas-01` | Plant-Wide Toxic Gas Continuous Area Monitor | CO_PPM · H2S_PPM · CO2_PPM · O2_PCT · VOC_INDEX | CO crit 25 ppm · H₂S crit 1 ppm · O₂ crit 19.5% BELOW |
| `chem-boiler-01` | Process Steam Boiler Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · CO_PPM | Pressure warn 1200 / crit 1500 kPa · Temp crit 250°C · CO crit 100 ppm |
| `chem-nitrogen-blanket-01` | Inert Nitrogen Blanketing System Monitor | PRESSURE · FLOW_LPM · O2_PCT · TEMPERATURE | Pressure crit 5 kPa BELOW (N₂ blanket failure) · O₂ crit 2% (20000 ppm) ABOVE (air ingress) |
| `chem-effluent-01` | Chemical Effluent Treatment Plant Monitor | PH · TEMPERATURE · WATER_LEVEL_PCT · CO_PPM | pH warn 10.0 / crit 11.0 ABOVE · Level crit 85% ABOVE |
| `chem-acid-tank-01` | Sulfuric / Hydrochloric Acid Storage Tank Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX · CO_PPM | Level warn 90% / crit 95% ABOVE · VOC crit 300 (acid mist proxy) · Temp crit 40°C |
| `chem-polymer-reactor-01` | Polymerisation Reactor Heat & Pressure Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · VIBRATION_G | Temp warn 150 / crit 180°C (runaway risk) · Pressure crit 2500 kPa · Vib crit 5g |
| `chem-instrument-air-01` | Plant Instrument Air & ISA Dryer Monitor | PRESSURE · FLOW_LPM · HUMIDITY · TEMPERATURE | Pressure crit 600 kPa BELOW · Humidity crit 30% (dew point proxy) · Temp crit 50°C |
| `chem-flare-01` | Chemical Plant Flare Stack Monitor | TEMPERATURE · FLOW_LPM · CO_PPM · VOC_INDEX | Temp warn 700 / crit 1100°C · CO crit 100 ppm |
| `chem-transformer-01` | Chemical Plant Substation & Transformer Monitor | TEMPERATURE · CURRENT_A · HUMIDITY · VOLTAGE_V | Temp warn 70°C / crit 85°C · Humidity crit 80% · Current crit 500A |
| `chem-cooling-tower-01` | Chemical Plant Cooling Tower Monitor | TEMPERATURE · FLOW_LPM · VIBRATION_G · WATER_LEVEL_PCT | Temp crit 45°C (outlet) · Flow crit 200 LPM BELOW · Level crit 20% BELOW · Vib crit 4g |

### 13. Marine & Port (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `marine-crane-01` | Port Container Crane Structural Health Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 4g / crit 8g · Current crit 500A · Sound crit 110 dB |
| `marine-vessel-01` | Marine Vessel Engine Room Health Monitor | TEMPERATURE · VIBRATION_G · PRESSURE · CURRENT_A | Temp crit 95°C (engine) · Vib crit 7g · Pressure crit 700 kPa |
| `marine-fuel-01` | Ship Bunker Fuel Tank Level & Vapour Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX | Level crit 5% BELOW · VOC crit 400 (LEL proxy) · Temp crit 60°C |
| `marine-bilge-01` | Ship Bilge Water Flooding & Gas Safety Monitor | WATER_LEVEL_PCT · CO_PPM · H2S_PPM · TEMPERATURE | Level warn 30% / crit 60% ABOVE · H₂S crit 10 ppm · CO crit 35 ppm |
| `marine-reefer-hold-01` | Refrigerated Cargo Hold Climate Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn -17°C / crit -14°C ABOVE · CO₂ crit 5000 ppm |
| `marine-mooring-01` | Mooring Line Tension & Weather Safety Monitor | VIBRATION_G · PRESSURE · TEMPERATURE · HUMIDITY | Vib crit 6g (snap load) · Pressure crit 950 hPa BELOW (storm) · Humidity crit 95% |
| `marine-sts-crane-01` | Ship-to-Shore (STS) Gantry Crane Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 4g / crit 8g · Current crit 600A · Sound crit 100 dB |
| `marine-rtg-crane-01` | Rubber-Tyred Gantry (RTG) Crane Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · TILT_DEG | Vib warn 3g / crit 6g · Current crit 300A · Tilt crit 3° ABOVE |
| `marine-vessel-bridge-01` | Ship Navigation Bridge & Wheelhouse Monitor | TEMPERATURE · HUMIDITY · PRESSURE · VIBRATION_G | Humidity crit 75% · Pressure crit 950 hPa BELOW (storm) · Vib crit 4g |
| `marine-cargo-hold-01` | Dry Bulk Cargo Hold Atmosphere Monitor | CO_PPM · CO2_PPM · O2_PCT · TEMPERATURE | CO crit 25 ppm · O₂ crit 19.5% BELOW (cargo off-gassing) · CO₂ crit 3000 ppm |
| `marine-ballast-01` | Ballast Water Treatment & Tank Monitor | WATER_LEVEL_PCT · PH · TEMPERATURE · FLOW_LPM | Level warn 85% / crit 95% ABOVE · pH crit 10.0 ABOVE (treatment) · Flow crit 500 LPM BELOW |
| `marine-sewage-01` | Ship Sewage Treatment Plant Monitor | TEMPERATURE · PH · WATER_LEVEL_PCT · CO_PPM | Level warn 80% / crit 90% ABOVE · pH warn 9.0 / crit 10.0 ABOVE · CO crit 25 ppm |
| `marine-reefer-container-01` | Reefer Container Yard Plug Monitor | TEMPERATURE · HUMIDITY · CURRENT_A · VOLTAGE_V | Temp warn -17°C / crit -15°C ABOVE · Current crit 30A · Voltage crit 200V BELOW |
| `marine-tug-01` | Port Tug Boat Engine Room Monitor | TEMPERATURE · VIBRATION_G · PRESSURE · CURRENT_A | Temp crit 90°C · Vib crit 6g · Pressure crit 500 kPa |
| `marine-oil-terminal-01` | Marine Oil Terminal & Loading Buoy Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · H2S_PPM | Flow crit 3000 LPM ABOVE · H₂S crit 10 ppm · Pressure crit 1000 kPa |
| `marine-port-gate-01` | Port Gate House Air Quality & Security Monitor | CO_PPM · CO2_PPM · TEMPERATURE · MOTION | CO warn 15 / crit 30 ppm (vehicle exhaust) · CO₂ crit 2000 ppm |
| `marine-dock-leveller-01` | RoRo Ramp & Linkspan Structural Monitor | VIBRATION_G · TILT_DEG · CURRENT_A · TEMPERATURE | Vib crit 5g · Tilt warn 3° / crit 5° ABOVE · Current crit 150A |
| `marine-hazmat-warehouse-01` | Port Dangerous Goods Warehouse Monitor | TEMPERATURE · HUMIDITY · CO_PPM · VOC_INDEX · H2S_PPM | Temp crit 35°C · CO crit 25 ppm · H₂S crit 5 ppm · VOC crit 400 |
| `marine-dry-dock-01` | Dry Dock Dewatering & Structural Monitor | WATER_LEVEL_PCT · PRESSURE · VIBRATION_G · CURRENT_A | Level crit 20% ABOVE (flooding risk) · Vib crit 4g · Current crit 100A |
| `marine-bow-thruster-01` | Vessel Bow & Stern Thruster Monitor | VIBRATION_G · TEMPERATURE · CURRENT_A · SOUND_DB | Vib warn 4g / crit 8g · Temp crit 90°C (motor) · Current crit 500A · Sound crit 120 dB |

### 14. Food & Beverage Processing (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `fbev-brewery-01` | Brewing Fermentation & Conditioning Tank Monitor | TEMPERATURE · PRESSURE · CO2_PPM · PH | Temp warn 15°C / crit 18°C ABOVE (lager fermentation) · CO₂ crit 5000 ppm · Pressure crit 350 kPa |
| `fbev-pasteuriser-01` | UHT / HTST Pasteurisation Line Monitor | TEMPERATURE · PRESSURE · FLOW_LPM | Temp crit 72°C BELOW (kill step failure) · Pressure crit 500 kPa · Flow crit 100 LPM BELOW |
| `fbev-bottling-01` | Bottling & CIP Line Health Monitor | TEMPERATURE · FLOW_LPM · PRESSURE · VIBRATION_G | Temp crit 90°C (CIP rinse) · Pressure crit 600 kPa · Vib crit 4g |
| `fbev-co2-storage-01` | CO₂ Carbonation Storage & Confined Space Safety Monitor | PRESSURE · TEMPERATURE · CO2_PPM · O2_PCT | Pressure crit 6000 kPa · CO₂ crit 5000 ppm · O₂ crit 19.5% BELOW |
| `fbev-oven-01` | Industrial Baking Oven & Proofing Chamber Monitor | TEMPERATURE · HUMIDITY · CO_PPM | Temp warn 220°C / crit 240°C · Humidity warn 90% ABOVE (proofer) · CO crit 35 ppm |
| `fbev-chiller-line-01` | Food Processing Chilled Production Line Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | Temp warn 4°C / crit 7°C · Humidity crit 90% · CO₂ crit 3000 ppm |
| `fbev-mixing-tank-01` | Industrial Mixing & Blending Tank Monitor | TEMPERATURE · PRESSURE · PH · VIBRATION_G | Temp warn 70°C / crit 85°C · pH warn 4.0 / crit 3.5 BELOW · Vib crit 3g |
| `fbev-homogeniser-01` | High-Pressure Homogeniser Monitor | PRESSURE · TEMPERATURE · VIBRATION_G · CURRENT_A | Pressure warn 15000 / crit 18000 kPa · Temp crit 80°C · Vib crit 5g |
| `fbev-spray-dryer-01` | Spray Dryer & Powder Collection Monitor | TEMPERATURE · HUMIDITY · PM25 · CO_PPM | Temp warn 180 / crit 200°C · Humidity crit 25% BELOW (dust explosion risk) · PM2.5 crit 50 µg/m³ |
| `fbev-retort-01` | Food Retort Sterilisation Autoclave Monitor | TEMPERATURE · PRESSURE · FLOW_LPM | Temp crit 121°C BELOW (F₀ kill step failure) · Pressure crit 300 kPa |
| `fbev-water-treatment-01` | Food Grade Water Treatment & RO Monitor | PH · FLOW_LPM · PRESSURE · TEMPERATURE | pH warn 7.5 / crit 8.0 ABOVE · Flow crit 200 LPM BELOW · Pressure crit 5000 kPa |
| `fbev-cold-room-01` | Food Finished Goods Cold Room Monitor | TEMPERATURE · HUMIDITY · CO2_PPM | Temp warn 3°C / crit 5°C ABOVE · Humidity crit 90% · CO₂ crit 3000 ppm |
| `fbev-effluent-01` | Food Processing Effluent Treatment Monitor | PH · WATER_LEVEL_PCT · TEMPERATURE · CO_PPM | pH warn 9.0 / crit 10.0 ABOVE · Level crit 85% ABOVE · CO crit 25 ppm |
| `fbev-sugar-silo-01` | Sugar / Flour Silo Explosion Prevention Monitor | TEMPERATURE · HUMIDITY · PM25 · CO_PPM | Humidity crit 15% BELOW (dust explosion risk) · PM2.5 crit 50 µg/m³ · CO crit 25 ppm |
| `fbev-boiler-01` | Food Plant Steam Boiler Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · CO_PPM | Pressure warn 700 / crit 900 kPa · Temp crit 180°C · CO crit 50 ppm |
| `fbev-cip-station-01` | CIP (Clean-In-Place) Caustic & Acid Station Monitor | TEMPERATURE · PH · FLOW_LPM · WATER_LEVEL_PCT | Temp warn 75°C / crit 85°C · pH warn 13.0 / crit 13.5 ABOVE · Level crit 10% BELOW |
| `fbev-fermentation-tank-01` | Spirit / Wine Fermentation Vat Monitor | TEMPERATURE · CO2_PPM · PRESSURE · PH | Temp warn 30°C / crit 35°C (spirit) · CO₂ crit 10000 ppm (confined space) · Pressure crit 200 kPa |
| `fbev-compressor-01` | Refrigeration Compressor Health Monitor | TEMPERATURE · PRESSURE · VIBRATION_G · CURRENT_A | Pressure warn 1200 / crit 1500 kPa · Temp crit 85°C · Vib crit 6g · Current crit 120A |
| `fbev-dry-goods-store-01` | Dry Goods & Ingredient Warehouse Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | Temp crit 28°C · Humidity warn 70% / crit 80% ABOVE · CO₂ crit 3000 ppm |
| `fbev-can-seamer-01` | Can Seaming & Metal Packaging Line Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 3g / crit 6g · Current crit 30A · Sound crit 95 dB · Temp crit 80°C |

### 15. Automotive Manufacturing (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `auto-paint-oven-01` | Vehicle Curing Oven (Paint Bake Oven) Monitor | TEMPERATURE · HUMIDITY · VOC_INDEX · CO_PPM | Temp warn 175°C / crit 190°C · VOC crit 350 (solvent LEL proxy) · CO crit 50 ppm |
| `auto-welding-cell-01` | Robotic Welding Cell Fume & Safety Monitor | SMOKE_PPM · CO_PPM · VOC_INDEX · VIBRATION_G | Smoke warn 200 / crit 400 ppm · CO crit 50 ppm · Vib crit 6g |
| `auto-press-shop-01` | Metal Stamping Press Health Monitor | VIBRATION_G · SOUND_DB · CURRENT_A · TEMPERATURE | Vib warn 5g / crit 9g · Sound crit 115 dB · Current crit 200A |
| `auto-ecoat-01` | Electrocoating (E-coat) Bath Process Monitor | TEMPERATURE · PH · CURRENT_A · VOLTAGE_V | Temp warn 30°C / crit 35°C · pH warn 6.5 / crit 6.0 BELOW · Voltage crit 400V ABOVE |
| `auto-test-cell-01` | Engine & Powertrain Test Cell Monitor | CO_PPM · CO2_PPM · TEMPERATURE · SOUND_DB | CO crit 50 ppm · CO₂ crit 5000 ppm · Sound crit 120 dB · Temp crit 80°C |
| `auto-body-shop-01` | Body Shop Stamping & Welding Fume Monitor | SMOKE_PPM · CO_PPM · VOC_INDEX · PM25 | Smoke warn 200 / crit 400 ppm · CO crit 35 ppm · PM2.5 crit 30 µg/m³ |
| `auto-final-assembly-01` | Final Assembly Line Environment Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · SOUND_DB | CO₂ crit 1500 ppm · Sound warn 80 / crit 85 dB · Temp crit 28°C |
| `auto-chassis-washer-01` | Chassis Washing & Pretreatment Line Monitor | TEMPERATURE · PH · FLOW_LPM · PRESSURE | Temp warn 55°C / crit 65°C · pH warn 11.0 / crit 12.0 ABOVE · Pressure crit 500 kPa |
| `auto-sealer-booth-01` | Underbody Sealer Application Booth Monitor | TEMPERATURE · HUMIDITY · VOC_INDEX · PM25 | VOC warn 300 / crit 400 · Humidity crit 70% · PM2.5 crit 25 µg/m³ |
| `auto-plastics-01` | Plastics Injection Moulding Monitor | TEMPERATURE · PRESSURE · VIBRATION_G · CURRENT_A | Temp warn 220°C / crit 250°C · Pressure crit 140000 kPa (injection) · Vib crit 4g |
| `auto-glass-oven-01` | Windscreen Laminating Oven Monitor | TEMPERATURE · HUMIDITY · PRESSURE | Temp warn 120°C / crit 135°C · Humidity crit 15% BELOW · Pressure crit 1500 kPa |
| `auto-battery-assembly-01` | EV Battery Pack Assembly & Formation Monitor | TEMPERATURE · VOLTAGE_V · CURRENT_A · HUMIDITY | Temp warn 35°C / crit 45°C · Voltage crit 500V ABOVE · Humidity crit 10% BELOW (dry room) |
| `auto-paint-mix-room-01` | Paint Mixing Room VOC & Explosion Prevention Monitor | TEMPERATURE · HUMIDITY · VOC_INDEX · CO_PPM | VOC warn 350 / crit 500 (LEL proxy) · Humidity crit 65% · CO crit 35 ppm |
| `auto-wastewater-01` | Automotive Paint Shop Wastewater Monitor | PH · TEMPERATURE · WATER_LEVEL_PCT · FLOW_LPM | pH warn 9.0 / crit 10.0 ABOVE · Level crit 85% ABOVE |
| `auto-end-of-line-01` | End-of-Line Vehicle Test & Emission Check Monitor | CO_PPM · CO2_PPM · VOC_INDEX · SOUND_DB | CO warn 20 / crit 35 ppm · CO₂ crit 3000 ppm · VOC crit 300 · Sound crit 110 dB |
| `auto-stamping-die-store-01` | Stamping Die & Tool Store Environment Monitor | TEMPERATURE · HUMIDITY · CO_PPM | Humidity warn 65% / crit 70% ABOVE (corrosion risk) · CO crit 25 ppm |
| `auto-ev-charging-01` | EV Assembly Plant High-Power Charging Station Monitor | CURRENT_A · VOLTAGE_V · TEMPERATURE · POWER_W | Current crit 400A · Voltage crit 1000V ABOVE · Temp crit 55°C · Power crit 250 kW |
| `auto-compressed-air-01` | Body Plant Compressed Air & Desiccant Dryer Monitor | PRESSURE · FLOW_LPM · HUMIDITY · TEMPERATURE | Pressure crit 600 kPa BELOW · Humidity crit 30% (instrument air) · Temp crit 45°C |
| `auto-torque-station-01` | Torque Nutrunner & Assembly Verification Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 3g / crit 6g · Current crit 25A · Sound crit 100 dB |
| `auto-hydraulic-press-01` | Stamping & Press Shop Hydraulic Press Monitor | PRESSURE · VIBRATION_G · TEMPERATURE · CURRENT_A | Pressure crit 25000 kPa · Vib warn 5g / crit 10g · Temp crit 80°C · Current crit 200A |

### 16. Railway & Transit (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `rail-track-01` | Railway Track Geometry & Structural Monitor | VIBRATION_G · TILT_DEG · TEMPERATURE | Vib warn 5g / crit 10g · Tilt warn 2° / crit 4° ABOVE · Temp crit 55°C (rail heat kink risk) |
| `rail-signal-01` | Railway Signalling Power & Environment Monitor | TEMPERATURE · HUMIDITY · VOLTAGE_V · CURRENT_A | Temp crit 60°C · Humidity crit 80% · Voltage crit 10V BELOW (DC supply rail) · Current crit 30A |
| `rail-tunnel-01` | Railway Tunnel Ventilation & Air Quality Monitor | CO_PPM · CO2_PPM · PM25 · TEMPERATURE | CO crit 50 ppm · CO₂ crit 3500 ppm · PM2.5 crit 50 µg/m³ |
| `rail-traction-power-01` | Traction Power Substation Monitor | TEMPERATURE · CURRENT_A · VOLTAGE_V · HUMIDITY | Temp warn 55°C / crit 70°C · Humidity crit 75% · Current crit 2000A |
| `rail-platform-01` | Station Platform Air Quality & Crowd Safety Monitor | CO2_PPM · TEMPERATURE · HUMIDITY · MOTION | CO₂ warn 1500 / crit 2500 ppm · Temp crit 35°C · Humidity crit 85% |
| `rail-switch-heater-01` | Railway Point / Switch Heater Monitor | TEMPERATURE · CURRENT_A · VIBRATION_G | Temp warn 60°C / crit 70°C (heater element) · Current crit 20A |
| `rail-overhead-wire-01` | Overhead Contact Line (OCL) Monitor | VOLTAGE_V · CURRENT_A · TEMPERATURE · VIBRATION_G | Voltage crit 22500V ABOVE (25kV AC) · Current crit 1000A · Vib crit 4g |
| `rail-bridge-01` | Railway Bridge Structural Health Monitor | VIBRATION_G · TILT_DEG · TEMPERATURE · HUMIDITY | Vib warn 5g / crit 10g · Tilt warn 1° / crit 3° ABOVE · Humidity crit 90% |
| `rail-level-crossing-01` | Level Crossing Barrier & Safety Monitor | MOTION · VIBRATION_G · TEMPERATURE · CURRENT_A | Vib crit 6g (vehicle collision) · Current crit 15A (barrier motor) · Temp crit 60°C |
| `rail-diesel-loco-01` | Diesel Locomotive Engine Room Health Monitor | TEMPERATURE · VIBRATION_G · CO_PPM · CURRENT_A | Temp crit 95°C · Vib crit 6g · CO crit 50 ppm · Current crit 1500A |
| `rail-emu-battery-01` | Electric Multiple Unit (EMU) Battery & Traction Monitor | BATTERY_PCT · TEMPERATURE · VOLTAGE_V · CURRENT_A | Battery crit 10% BELOW · Temp warn 40°C / crit 55°C · Voltage crit 650V ABOVE |
| `rail-depot-01` | Rolling Stock Maintenance Depot Air Quality Monitor | CO_PPM · CO2_PPM · TEMPERATURE · VIBRATION_G | CO crit 35 ppm · CO₂ crit 2000 ppm · Vib crit 3g |
| `rail-wayside-01` | Wayside Wheel Impact Load Detector (WILD) Monitor | VIBRATION_G · SOUND_DB · TEMPERATURE | Vib warn 8g / crit 15g (flat wheel / cracked rim) · Sound crit 130 dB |
| `rail-retarder-01` | Hump Yard Car Retarder Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · SOUND_DB | Vib warn 3g / crit 5g · Current crit 80A · Temp crit 70°C |
| `rail-catenary-mast-01` | Catenary Mast & Stagger Structural Monitor | VIBRATION_G · TILT_DEG · TEMPERATURE · HUMIDITY | Vib crit 5g (wind-induced) · Tilt warn 2° / crit 4° ABOVE · Humidity crit 90% |
| `rail-station-hvac-01` | Underground Station HVAC & Smoke Control Monitor | TEMPERATURE · CO_PPM · CO2_PPM · PRESSURE | CO crit 25 ppm · CO₂ crit 2000 ppm · Pressure crit 100 Pa BELOW (piston effect) |
| `rail-fuel-depot-01` | Rail Diesel Fuel Depot Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX · CO_PPM | Level warn 90% / crit 95% ABOVE · VOC crit 400 · CO crit 25 ppm |
| `rail-control-room-01` | Train Control Centre (TCC) Environment Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | Temp warn 20°C / crit 26°C · Humidity crit 60% · CO₂ crit 1000 ppm |
| `rail-wheel-lathe-01` | Wheel Lathe & Axle Shop Monitor | VIBRATION_G · SOUND_DB · TEMPERATURE · CURRENT_A | Vib warn 4g / crit 7g · Sound crit 110 dB · Current crit 200A |
| `rail-catenary-01` | Overhead Catenary & Pantograph Wear Monitor | VOLTAGE_V · CURRENT_A · VIBRATION_G · TEMPERATURE | Voltage warn 24000V / crit 27500V ABOVE · Current crit 1000A · Vib crit 5g |

### 17. Semiconductor Fab (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `semi-fab-env-01` | Semiconductor Fab Bay Environmental Monitor (ISO Class 5) | TEMPERATURE · HUMIDITY · PM25 · PM10 · PRESSURE | PM2.5 crit 3.5 µg/m³ · Humidity crit 42% (electrostatic risk) · Temp crit 22°C |
| `semi-upw-01` | Ultrapure Water (UPW) System Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · PH | Flow crit 500 LPM BELOW · pH warn 7.1 / crit 7.2 ABOVE · Temp crit 25°C |
| `semi-chemical-delivery-01` | Process Chemical Delivery System Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · PH | Flow crit 5 LPM BELOW · Pressure crit 400 kPa · pH warn 3.0 / crit 2.5 BELOW |
| `semi-exhaust-01` | Fab Exhaust & Abatement System Monitor | CO_PPM · VOC_INDEX · TEMPERATURE · FLOW_LPM | CO crit 25 ppm · VOC crit 300 · Temp crit 800°C (abatement combustion) |
| `semi-chiller-01` | Fab Precision Process Chiller Monitor | TEMPERATURE · FLOW_LPM · PRESSURE · CURRENT_A | Temp crit 21°C ABOVE (process drift) · Flow crit 200 LPM BELOW · Current crit 100A |
| `semi-photolithography-01` | Photolithography Track & Stepper Environment Monitor | TEMPERATURE · HUMIDITY · PM25 · PRESSURE | Temp crit 23°C ABOVE · Humidity crit 40% (photoresist) · PM2.5 crit 1 µg/m³ |
| `semi-etch-01` | Plasma Etch Tool Exhaust & Gas Safety Monitor | CO_PPM · VOC_INDEX · CO2_PPM · TEMPERATURE | CO crit 25 ppm · VOC crit 300 · Temp crit 60°C (exhaust line) |
| `semi-cvd-01` | Chemical Vapour Deposition (CVD) Tool Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · CO_PPM | Temp warn 400 / crit 450°C (chamber) · Pressure crit 5 kPa ABOVE · CO crit 25 ppm |
| `semi-acid-room-01` | Fab Acid & Chemical Waste Room Safety Monitor | CO_PPM · VOC_INDEX · H2S_PPM · O2_PCT | CO crit 25 ppm · VOC crit 300 · H₂S crit 1 ppm · O₂ crit 19.5% BELOW |
| `semi-bulk-gas-01` | Bulk Process Gas Storage & Manifold Monitor | PRESSURE · FLOW_LPM · O2_PCT · TEMPERATURE | Pressure crit 200 kPa BELOW (gas low) · O₂ crit 19.5% BELOW (inert gas leak) · Temp crit 50°C |
| `semi-scrubber-01` | Wet Scrubber & Point-of-Use Abatement Monitor | CO_PPM · VOC_INDEX · FLOW_LPM · TEMPERATURE | CO crit 25 ppm · Flow crit 200 LPM BELOW (scrubber failure) · Temp crit 70°C |
| `semi-nitrogen-gen-01` | Nitrogen Generator & Distribution Monitor | PRESSURE · FLOW_LPM · O2_PCT · TEMPERATURE | O₂ crit 100 ppm ABOVE (N₂ purity loss proxy) · Pressure crit 600 kPa BELOW |
| `semi-wafer-storage-01` | Wafer Storage & SMIF Pod Environment Monitor | TEMPERATURE · HUMIDITY · PM25 · VIBRATION_G | Temp crit 23°C ABOVE · Humidity crit 35% · PM2.5 crit 0.5 µg/m³ · Vib crit 0.5g |
| `semi-diffusion-01` | Diffusion Furnace & Oxidation Tube Monitor | TEMPERATURE · FLOW_LPM · CO_PPM · PRESSURE | Temp warn 1050 / crit 1100°C · CO crit 25 ppm · Pressure crit 5 kPa |
| `semi-deionised-water-01` | Deionised Water (DIW) System Monitor | FLOW_LPM · PRESSURE · TEMPERATURE · PH | Flow crit 1000 LPM BELOW · pH warn 7.1 / crit 7.2 ABOVE · Temp crit 23°C ABOVE |
| `semi-clean-dry-air-01` | Clean Dry Air (CDA) & Point-of-Use Monitor | PRESSURE · HUMIDITY · FLOW_LPM · TEMPERATURE | Pressure crit 500 kPa BELOW · Humidity crit 5% (CDA dew point) · Temp crit 35°C |
| `semi-vacuum-system-01` | Process Vacuum Pump Monitor | PRESSURE · TEMPERATURE · VIBRATION_G · CURRENT_A | Pressure crit 1 kPa ABOVE (vacuum loss) · Temp crit 80°C · Vib crit 4g |
| `semi-mask-room-01` | Reticle / Mask Storage Room Monitor | TEMPERATURE · HUMIDITY · PM25 · VIBRATION_G | Temp crit 22°C ABOVE · Humidity crit 35% · PM2.5 crit 0.1 µg/m³ · Vib crit 0.3g |
| `semi-ion-implant-01` | Ion Implanter Process & Vacuum Monitor | PRESSURE · TEMPERATURE · CURRENT_A · VIBRATION_G | Pressure crit 0.1 kPa ABOVE (vacuum loss) · Temp crit 70°C (magnet) · Current crit 50A |
| `semi-smif-pod-01` | Wafer SMIF Pod & Front-Opening Unified Pod (FOUP) Monitor | TEMPERATURE · HUMIDITY · VIBRATION_G · PRESSURE | Temp warn 22°C / crit 24°C · Humidity crit 40% · Vib crit 0.5g · Pressure crit ±5 Pa |

### 18. Hospitality & Hotels (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `hotel-room-01` | Smart Guest Room Climate & Occupancy Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION · LIGHT_LUX | CO₂ warn 1000 / crit 1500 ppm · Temp crit 28°C · Humidity crit 70% |
| `hotel-kitchen-01` | Hotel Commercial Kitchen Ventilation & Fire Safety | TEMPERATURE · CO_PPM · SMOKE_PPM · CO2_PPM | CO warn 25 / crit 50 ppm · Smoke crit 400 ppm · Temp crit 300°C (duct fire) |
| `hotel-laundry-01` | Hotel Laundry Room Steam & Heat Monitor | TEMPERATURE · HUMIDITY · PRESSURE · CO_PPM | Temp crit 80°C · Pressure crit 700 kPa (steam) · CO crit 25 ppm |
| `hotel-pool-spa-01` | Hotel Pool & Spa Water Quality Monitor | TEMPERATURE · PH · WATER_LEVEL_PCT | Temp warn 39°C / crit 41°C (spa) · pH warn 8.0 / crit 8.5 ABOVE |
| `hotel-boiler-01` | Hotel Central Boiler & Hot Water Plant Monitor | TEMPERATURE · PRESSURE · FLOW_LPM · CO_PPM | Pressure crit 900 kPa · Temp crit 90°C · CO crit 50 ppm |
| `hotel-mini-bar-01` | Guest Room Minibar & Wine Cabinet Monitor | TEMPERATURE · HUMIDITY | Temp warn 6°C / crit 8°C · Humidity crit 80% |
| `hotel-banquet-01` | Banquet Hall & Conference HVAC Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | CO₂ warn 1000 / crit 1800 ppm · Temp crit 26°C · Humidity crit 65% |
| `hotel-gym-01` | Hotel Gymnasium & Fitness Centre Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · SOUND_DB | CO₂ warn 1500 / crit 2000 ppm · Temp crit 27°C · Sound crit 85 dB |
| `hotel-server-room-01` | Hotel Server & AV Equipment Room Monitor | TEMPERATURE · HUMIDITY | Temp warn 25°C / crit 30°C · Humidity warn 20% / crit 15% BELOW |
| `hotel-linen-store-01` | Linen Store & Laundry Collection Monitor | TEMPERATURE · HUMIDITY · MOTION | Humidity warn 70% / crit 80% ABOVE (mould risk) · Temp crit 30°C |
| `hotel-fire-system-01` | Hotel Fire Suppression & Sprinkler Monitor | TEMPERATURE · SMOKE_PPM · PRESSURE · WATER_LEVEL_PCT | Smoke warn 200 / crit 400 ppm · Pressure crit 500 kPa BELOW |
| `hotel-parking-01` | Hotel Underground Car Park Air Quality Monitor | CO_PPM · CO2_PPM · TEMPERATURE | CO warn 20 / crit 35 ppm · CO₂ crit 2500 ppm · Temp crit 38°C |
| `hotel-restaurant-01` | Hotel Restaurant Kitchen & Dining Air Monitor | TEMPERATURE · CO_PPM · SMOKE_PPM · CO2_PPM | CO warn 20 / crit 35 ppm · CO₂ crit 1500 ppm · Smoke crit 300 ppm |
| `hotel-wine-cellar-01` | Hotel Wine Cellar & Spirits Store Monitor | TEMPERATURE · HUMIDITY · LIGHT_LUX · VIBRATION_G | Temp warn 13°C / crit 16°C ABOVE · Humidity crit 70% · Light crit 200 lux · Vib crit 1g |
| `hotel-generator-01` | Hotel Emergency Generator & Fuel Tank Monitor | TEMPERATURE · VIBRATION_G · CURRENT_A · WATER_LEVEL_PCT | Temp crit 90°C · Vib crit 5g · Level crit 20% BELOW (fuel) |
| `hotel-elevator-01` | Hotel Elevator Machine Room Monitor | TEMPERATURE · VIBRATION_G · CURRENT_A · HUMIDITY | Temp warn 35°C / crit 45°C · Vib crit 3g · Current crit 50A |
| `hotel-spa-01` | Hotel Spa & Steam Room Monitor | TEMPERATURE · HUMIDITY · CO_PPM · PRESSURE | Temp warn 40°C / crit 45°C (steam room) · Humidity warn 95% ABOVE · CO crit 25 ppm |
| `hotel-guest-corridor-01` | Hotel Guest Corridor & Common Area Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | CO₂ crit 1200 ppm · Temp crit 28°C · Humidity crit 65% |
| `hotel-rooftop-hvac-01` | Hotel Rooftop HVAC Condensing Unit Monitor | TEMPERATURE · CURRENT_A · VIBRATION_G · PRESSURE | Temp crit 60°C (refrigerant) · Current crit 80A · Vib crit 4g · Pressure crit 1800 kPa |
| `hotel-grease-trap-01` | Hotel Restaurant Grease Trap & Fat Separator Monitor | WATER_LEVEL_PCT · TEMPERATURE · FLOW_LPM | Level warn 70% / crit 85% ABOVE · Temp crit 60°C (effluent limit) |

### 19. Retail & Supermarket (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `retail-display-fridge-01` | Supermarket Open-Front Refrigerated Display Case | TEMPERATURE · HUMIDITY | Temp warn 5°C / crit 8°C · Humidity crit 90% |
| `retail-island-freezer-01` | Supermarket Island Chest Freezer Monitor | TEMPERATURE · HUMIDITY | Temp warn -17°C / crit -15°C ABOVE · Humidity crit 85% |
| `retail-hvac-01` | Retail Store HVAC & Indoor Air Quality Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · PM25 | CO₂ warn 1000 / crit 1500 ppm · Temp crit 26°C · PM2.5 crit 25 µg/m³ |
| `retail-bakery-01` | In-Store Bakery Oven & Proofing Monitor | TEMPERATURE · HUMIDITY · CO_PPM | Temp warn 220°C / crit 240°C · CO warn 20 / crit 35 ppm · Humidity warn 85% ABOVE (proofer) |
| `retail-wine-display-01` | Wine & Spirits Climate-Controlled Display Monitor | TEMPERATURE · HUMIDITY · LIGHT_LUX | Temp warn 16°C / crit 18°C ABOVE · Humidity crit 70% · Light crit 300 lux |
| `retail-meat-display-01` | Meat & Deli Service Counter Monitor | TEMPERATURE · HUMIDITY | Temp warn 3°C / crit 5°C ABOVE · Humidity crit 90% |
| `retail-produce-mist-01` | Fresh Produce Misting & Display Monitor | TEMPERATURE · HUMIDITY · WATER_LEVEL_PCT | Temp crit 8°C ABOVE · Humidity warn 85% / crit 95% ABOVE · Level crit 5% BELOW |
| `retail-pharmacy-01` | Pharmacy & Health Product Storage Monitor | TEMPERATURE · HUMIDITY · LIGHT_LUX | Temp warn 23°C / crit 25°C · Humidity crit 60% · Light crit 400 lux |
| `retail-loading-01` | Retail Store Loading Bay Monitor | TEMPERATURE · HUMIDITY · CO_PPM · MOTION | CO crit 35 ppm (vehicle exhaust) · Temp crit 38°C |
| `retail-refrigeration-plant-01` | Retail Refrigeration Plant Room Monitor | TEMPERATURE · PRESSURE · CURRENT_A · VIBRATION_G | Temp crit 35°C (ambient) · Pressure crit 1200 kPa · Vib crit 4g |
| `retail-fire-system-01` | Retail Store Fire Suppression & Safety Monitor | TEMPERATURE · SMOKE_PPM · CO_PPM · PRESSURE | Smoke warn 200 / crit 400 ppm · CO crit 35 ppm · Pressure crit 500 kPa BELOW |
| `retail-ev-charger-01` | Retail Car Park EV Charging Station Monitor | CURRENT_A · VOLTAGE_V · TEMPERATURE · POWER_W | Current crit 64A · Voltage crit 400V ABOVE · Temp crit 55°C · Power crit 22 kW |
| `retail-escalator-01` | Retail Escalator & Travelator Monitor | CURRENT_A · VIBRATION_G · TEMPERATURE · SOUND_DB | Current crit 40A · Vib warn 2g / crit 4g · Temp crit 65°C · Sound crit 80 dB |
| `retail-it-room-01` | Retail IT Server & POS System Room Monitor | TEMPERATURE · HUMIDITY | Temp warn 25°C / crit 30°C · Humidity crit 60% |
| `retail-stockroom-01` | Stockroom & Back-of-House Environment Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | Temp crit 30°C · Humidity warn 70% / crit 80% ABOVE · CO₂ crit 2000 ppm |
| `retail-water-leak-01` | Retail Floor & Plumbing Water Leak Detector | WATER_LEVEL_PCT · TEMPERATURE | Water warn 1% / crit 5% ABOVE · Temp crit 60°C (hot pipe burst) |
| `retail-outdoor-unit-01` | Rooftop HVAC Outdoor Unit Monitor | TEMPERATURE · CURRENT_A · VIBRATION_G · PRESSURE | Temp crit 65°C (refrigerant) · Current crit 100A · Vib crit 4g · Pressure crit 1800 kPa |
| `retail-fuel-station-01` | Petrol / Fuel Station Underground Tank Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX · PRESSURE | Level crit 10% BELOW · VOC crit 400 (LEL proxy) · Pressure crit 5 kPa ABOVE (leak) |
| `retail-generator-01` | Retail Store Emergency Generator Monitor | TEMPERATURE · VIBRATION_G · CURRENT_A · WATER_LEVEL_PCT | Temp crit 90°C · Vib crit 5g · Level crit 20% BELOW (fuel) |
| `retail-atm-01` | ATM & Cash Office Climate Monitor | TEMPERATURE · HUMIDITY · MOTION | Temp warn 25°C / crit 30°C · Humidity crit 65% · Motion crit (unauthorized access) |

### 20. Construction & Infrastructure (20 devices)

| Device ID | Description | Key Sensors | Alert Thresholds |
|---|---|---|---|
| `construct-crane-01` | Tower Crane Structural Load & Safety Monitor | VIBRATION_G · TILT_DEG · CURRENT_A · SOUND_DB | Vib warn 4g / crit 8g · Tilt warn 1° / crit 2° ABOVE · Current crit 300A |
| `construct-tunnel-01` | Tunnel Construction Air Quality & Safety Monitor | CO_PPM · CO2_PPM · PM25 · VIBRATION_G | CO crit 35 ppm · CO₂ crit 5000 ppm · PM2.5 crit 100 µg/m³ |
| `construct-structural-01` | Building Structure Monitoring During Construction | VIBRATION_G · TILT_DEG · TEMPERATURE · HUMIDITY | Vib crit 5g · Tilt warn 1° / crit 2° ABOVE · Humidity crit 90% (concrete curing) |
| `construct-dewatering-01` | Construction Site Dewatering Pump Monitor | WATER_LEVEL_PCT · FLOW_LPM · PRESSURE · CURRENT_A | Level crit 80% ABOVE · Current crit 80A · Pressure crit 500 kPa |
| `construct-concrete-01` | Concrete Pour Temperature & Curing Monitor | TEMPERATURE · HUMIDITY · PRESSURE | Temp warn 65°C / crit 75°C (hydration heat) · Humidity crit 90% |
| `construct-tower-block-01` | High-Rise Building Wind & Settlement Monitor | VIBRATION_G · TILT_DEG · TEMPERATURE · HUMIDITY | Vib warn 2g / crit 5g · Tilt warn 1° / crit 2° ABOVE · Humidity crit 90% |
| `construct-piling-01` | Pile Driving & Foundation Vibration Monitor | VIBRATION_G · SOUND_DB · TILT_DEG · TEMPERATURE | Vib warn 5g / crit 10g · Sound crit 130 dB · Tilt crit 3° ABOVE |
| `construct-formwork-01` | Formwork & Falsework Pressure Monitor | PRESSURE · VIBRATION_G · TEMPERATURE · HUMIDITY | Pressure warn 80 / crit 100 kPa (fresh concrete lateral) · Vib crit 5g · Humidity crit 95% |
| `construct-generator-01` | Construction Site Diesel Generator Monitor | TEMPERATURE · VIBRATION_G · CO_PPM · CURRENT_A | Temp crit 95°C · Vib crit 5g · CO crit 50 ppm · Current crit 300A |
| `construct-air-quality-01` | Construction Site Ambient Air Quality Monitor | PM25 · PM10 · CO_PPM · TEMPERATURE | PM2.5 warn 25 / crit 50 µg/m³ · PM10 crit 100 µg/m³ · CO crit 35 ppm |
| `construct-noise-01` | Construction Site Noise & Vibration Monitor | SOUND_DB · VIBRATION_G · TEMPERATURE | Sound warn 70 / crit 75 dB (neighbourhood limit) · Vib crit 5g |
| `construct-scaffold-01` | Scaffold & Access Platform Structural Monitor | VIBRATION_G · TILT_DEG · PRESSURE · HUMIDITY | Vib crit 4g · Tilt warn 2° / crit 3° ABOVE · Humidity crit 90% |
| `construct-welding-01` | Construction Site Welding & Gas Cutting Monitor | CO_PPM · VOC_INDEX · SMOKE_PPM · TEMPERATURE | CO crit 35 ppm · Smoke warn 200 / crit 400 ppm · VOC crit 300 |
| `construct-fuel-store-01` | Construction Site Fuel Storage Monitor | WATER_LEVEL_PCT · TEMPERATURE · VOC_INDEX · MOTION | Level crit 10% BELOW · VOC crit 400 · Temp crit 40°C |
| `construct-hoist-01` | Construction Material & Personnel Hoist Monitor | VIBRATION_G · CURRENT_A · TEMPERATURE · TILT_DEG | Vib warn 3g / crit 6g · Current crit 80A · Tilt crit 2° ABOVE |
| `construct-water-pump-01` | Construction Dewatering & Water Supply Pump Monitor | FLOW_LPM · PRESSURE · WATER_LEVEL_PCT · CURRENT_A | Level crit 80% ABOVE · Pressure crit 600 kPa · Flow crit 500 LPM BELOW |
| `construct-curing-tent-01` | Heated Curing Tent & Winter Concreting Monitor | TEMPERATURE · HUMIDITY · CO_PPM | Temp warn 5°C / crit 2°C BELOW · CO crit 35 ppm (propane heater) · Humidity crit 90% |
| `construct-hazmat-store-01` | Construction Hazardous Material Store Monitor | TEMPERATURE · HUMIDITY · CO_PPM · VOC_INDEX | Temp crit 35°C · CO crit 25 ppm · VOC warn 300 / crit 450 |
| `construct-anchor-01` | Ground Anchor & Retaining Wall Monitor | VIBRATION_G · TILT_DEG · PRESSURE · TEMPERATURE | Vib crit 4g · Tilt warn 2° / crit 3° ABOVE · Pressure crit 500 kPa ABOVE |
| `construct-ppe-01` | Site Welfare Facility Air Quality & Environment Monitor | TEMPERATURE · HUMIDITY · CO2_PPM · MOTION | CO₂ crit 1500 ppm · Temp crit 30°C · Humidity crit 70% |

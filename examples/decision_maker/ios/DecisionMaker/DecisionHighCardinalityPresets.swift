// Copyright 2026 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import Foundation

/// High-cardinality polymorphic scenario presets (20, 50, and 100 options) showcasing
/// EmbeddingGemma-2 270M's O(1) single-pass query evaluation after option prewarming.
public enum DecisionHighCardinalityPresets {
  private struct RawOptionSeed {
    let label: String
    let description: String
    let examples: String
  }

  private static func buildOptions(_ seeds: [RawOptionSeed]) -> [DecisionOptionItem] {
    seeds.map {
      DecisionOptionItem(label: $0.label, description: $0.description, examples: $0.examples)
    }
  }

  public static let all: [ScenarioPreset] = [
    cloudIncident20Preset,
    ecommerceCatalog50Preset,
    assistantIntents100Preset,
  ]

  public static let cloudIncident20Preset = ScenarioPreset(
    id: "cloud_incident_20",
    title: "Cloud SRE Router (20 Options)",
    subtitle:
      "EG2 Showcase: 20 Engineering Queues [Choice] + Page On-Call [Boolean] + Severity 1..5 [Score]",
    domainContext:
      "You are an automated SRE incident dispatcher routing incoming cloud infrastructure alerts and customer incident reports across 20 specialized engineering queues, determining whether to page on-call immediately, and scoring severity from 1 to 5.",
    candidateQueries: [
      "BGP route leak in us-east1 is dropping 40% of ingress packets across external load balancers.",
      "Kubernetes pod eviction storm on GKE production node pool due to cgroup OOMKiller memory exhaustion.",
      "BigQuery scheduled nightly ETL query failed with slot quota exceeded error in analytics project.",
      "HBM ECC uncorrectable memory error on TPU v5p training slice halting distributed checkpoint step.",
    ],
    questions: [
      PolymorphicQuestionSpec(
        id: "page_oncall",
        type: .boolean,
        prompt:
          "Does this incident require immediately paging the 24/7 on-call SRE engineer due to active production impact?",
        options: [
          DecisionOptionItem(
            label: "true",
            description:
              "Active production packet loss, cluster node eviction storm, hardware accelerator fault, or customer-facing outage requiring immediate on-call intervention.",
            examples:
              "'BGP route leak dropping 40% of ingress packets', 'Kubernetes pod eviction storm on production node pool', 'Uncorrectable HBM ECC error halting production training cluster'"
          ),
          DecisionOptionItem(
            label: "false",
            description:
              "Non-urgent batch ETL quota warning, staging configuration request, or routine capacity inquiry that can be handled during business hours.",
            examples:
              "'Scheduled nightly analytics ETL query hit slot quota limit', 'Please enable point-in-time recovery on our staging database', 'Question about committed-use discount billing tier'"
          ),
        ]
      ),
      PolymorphicQuestionSpec(
        id: "engineering_queue",
        type: .choice,
        prompt:
          "Which of the 20 specialized cloud infrastructure engineering queues should own this incident?",
        options: buildOptions([
          RawOptionSeed(
            label: "edge_load_balancing",
            description:
              "External L4/L7 load balancers, Anycast VIPs, TLS termination, and HTTP/3 ingress proxy errors.",
            examples:
              "'Global L7 load balancer returning 502 Bad Gateway', 'TLS handshake timeouts on edge proxy VIP', 'HTTP/3 QUIC connection drops at ingress'"
          ),
          RawOptionSeed(
            label: "bgp_core_networking",
            description:
              "BGP peering sessions, route leaks, backbone fiber cuts, and cross-region WAN packet loss.",
            examples:
              "'BGP route leak in us-east1 dropping 40% of ingress packets', 'Subsea backbone fiber cut causing 180ms latency spike', 'Peering session flap at transit exchange'"
          ),
          RawOptionSeed(
            label: "dns_resolution",
            description:
              "Authoritative Cloud DNS zones, recursive resolver SERVFAIL spikes, and DNSSEC validation errors.",
            examples:
              "'Recursive DNS resolvers returning SERVFAIL for internal zones', 'DNSSEC RRSIG expiration breaking domain lookup', 'TTL propagation delay across authoritative nameservers'"
          ),
          RawOptionSeed(
            label: "cdn_media_cache",
            description:
              "CDN edge cache hit-ratio drops, origin shield overload, signed URL failures, and HLS segment 404s.",
            examples:
              "'CDN cache hit ratio dropped to 12% after purge', 'HLS video streaming segments returning 403 invalid signature', 'Origin shield saturated by uncached static assets'"
          ),
          RawOptionSeed(
            label: "kubernetes_control_plane",
            description:
              "GKE/Kubernetes kube-apiserver latency, etcd quorum loss, pod eviction storms, and cgroup OOMKills.",
            examples:
              "'Kubernetes pod eviction storm on GKE node pool due to cgroup OOMKiller', 'kube-apiserver webhook timeout blocking deployments', 'etcd leader election thrashing in cluster'"
          ),
          RawOptionSeed(
            label: "serverless_functions",
            description:
              "Serverless container and function cold-start timeouts, concurrency autoscaling, and sandbox crashes.",
            examples:
              "'Cloud Run revision failing readiness probe on cold start', 'Serverless function throttled at max concurrency limit', 'Container sandbox gVisor segfault on invocation'"
          ),
          RawOptionSeed(
            label: "vm_hypervisor_compute",
            description:
              "KVM/hypervisor live migration stalls, guest OS kernel panics, and vCPU noisy-neighbor steal time.",
            examples:
              "'VM instance froze during host live maintenance migration', 'Guest Linux kernel panic in virtio-net driver', 'High CPU steal time on multi-tenant compute node'"
          ),
          RawOptionSeed(
            label: "gpu_tpu_accelerators",
            description:
              "GPU/TPU HBM ECC memory errors, NVLink/ICI interconnect hangs, and XLA/CUDA driver crashes.",
            examples:
              "'HBM ECC uncorrectable memory error on TPU v5p training slice', 'NCCL all-reduce ring hang across 64 H100 GPUs', 'CUDA driver Xid 79 fallen off the bus'"
          ),
          RawOptionSeed(
            label: "relational_database_sql",
            description:
              "Managed PostgreSQL/MySQL primary failover, read-replica lag, deadlock storms, and PITR backups.",
            examples:
              "'PostgreSQL primary instance stuck in recovery after failover', 'MySQL read replica lag exceeding 45 minutes', 'Enable point-in-time recovery backups on staging SQL'"
          ),
          RawOptionSeed(
            label: "distributed_nosql_spanner",
            description:
              "Globally distributed transactional database Paxos consensus latency, tablet splits, and hot-spotting.",
            examples:
              "'Spanner commit latency spike due to monotonic key hotspot', 'Paxos leader election timeout across multi-region quorum', 'Transaction abort storm on secondary index'"
          ),
          RawOptionSeed(
            label: "in_memory_cache_redis",
            description:
              "Redis and Memcached cluster slot migrations, key eviction spikes, and connection pool exhaustion.",
            examples:
              "'Redis cluster OOM evicting session keys under peak traffic', 'Redis sentinel failover left stale replica connections', 'Memcached slab calcification causing cache misses'"
          ),
          RawOptionSeed(
            label: "object_blob_storage",
            description:
              "Cloud object storage bucket 503 slow-down throttling, multipart upload corruption, and lifecycle rules.",
            examples:
              "'Object storage bucket returning 503 Slow Down on prefix', 'Multipart upload checksum mismatch on 50GB archive', 'Object lifecycle retention policy failed to archive cold blobs'"
          ),
          RawOptionSeed(
            label: "block_storage_disks",
            description:
              "NVMe persistent block volume IOPS throttling, read-only filesystem remounts, and snapshot failures.",
            examples:
              "'Persistent NVMe disk throttled at max write throughput', 'EXT4 filesystem remounted read-only after I/O timeout', 'Incremental volume snapshot stuck at 99%'"
          ),
          RawOptionSeed(
            label: "stream_messaging_kafka",
            description:
              "Pub/Sub and Kafka consumer group lag, dead-letter queue backlog, and broker partition imbalance.",
            examples:
              "'Kafka consumer group lag exceeded 10 million messages', 'Pub/Sub dead-letter topic filling up with poison pills', 'Broker disk full on partition leader replica'"
          ),
          RawOptionSeed(
            label: "data_warehouse_analytics",
            description:
              "BigQuery/Spark analytical query slot quota exhaustion, shuffle spill to disk, and nightly ETL failures.",
            examples:
              "'BigQuery scheduled nightly ETL query failed with slot quota exceeded', 'Spark shuffle stage OOM spilling 2TB to scratch disk', 'Materialized view refresh failed on schema drift'"
          ),
          RawOptionSeed(
            label: "iam_identity_sso",
            description:
              "OAuth2/OIDC token issuance, enterprise SAML SSO assertions, and service account IAM permission denials.",
            examples:
              "'Workload identity federation rejecting OIDC token from GitHub Actions', 'SAML SSO login failing with invalid signature', 'Service account missing iam.serviceAccountTokenCreator role'"
          ),
          RawOptionSeed(
            label: "kms_secrets_encryption",
            description:
              "Cloud KMS hardware security module (HSM) key rotation, envelope decryption, and Vault certificate expiry.",
            examples:
              "'KMS customer-managed encryption key disabled breaking disk mount', 'HSM key rotation job failed quorum approval', 'mTLS client certificate expired in secret manager'"
          ),
          RawOptionSeed(
            label: "ci_cd_build_artifacts",
            description:
              "Artifact container registry pull rate limits, remote build cache corruption, and canary rollout gates.",
            examples:
              "'Container registry returning 429 Too Many Requests during node scale-up', 'Remote Bazel cache returning corrupted action digest', 'Automated canary deployment halted by error-budget gate'"
          ),
          RawOptionSeed(
            label: "observability_metrics_logs",
            description:
              "Prometheus/OpenTelemetry metric ingestion lag, distributed trace sampling drops, and alert rule evaluation.",
            examples:
              "'OpenTelemetry collector dropping 60% of trace spans due to queue full', 'Prometheus TSDB WAL corruption on monitoring shard', 'Log ingestion pipeline delayed by 25 minutes'"
          ),
          RawOptionSeed(
            label: "billing_quota_governance",
            description:
              "Cloud project GPU/CPU quota increase requests, committed-use discount anomalies, and budget alerts.",
            examples:
              "'Requesting quota increase for 128 A100 GPUs in us-central1', 'Committed-use discount not applying to new GKE node pool', 'Daily cloud spend exceeded budget alert threshold'"
          ),
        ])
      ),
      PolymorphicQuestionSpec(
        id: "incident_severity",
        type: .score,
        prompt:
          "Rate the cloud incident severity from 1 (Routine Request) to 5 (Critical Multi-Region Outage).",
        options: [
          DecisionOptionItem(
            label: "1",
            description:
              "Level 1 (Routine Request): Quota inquiry, staging config change, or billing question.",
            examples: "'Enable backups on staging SQL', 'Question about committed-use discount'"
          ),
          DecisionOptionItem(
            label: "2",
            description:
              "Level 2 (Minor Warning): Non-critical batch job retry or single-job quota warning.",
            examples:
              "'Nightly analytics ETL query hit slot quota limit', 'Canary build cache miss'"
          ),
          DecisionOptionItem(
            label: "3",
            description:
              "Level 3 (Degraded Service): Elevated latency or replica lag with automatic mitigation active.",
            examples: "'Read replica lag at 20 minutes', 'Observability log ingestion delayed'"
          ),
          DecisionOptionItem(
            label: "4",
            description:
              "Level 4 (Major Production Impact): Production GKE node evictions or TPU training slice halt.",
            examples:
              "'Kubernetes pod eviction storm on production node pool', 'HBM ECC error halting TPU slice'"
          ),
          DecisionOptionItem(
            label: "5",
            description:
              "Level 5 (Critical Outage): Widespread BGP packet loss or global ingress failure.",
            examples:
              "'BGP route leak in us-east1 dropping 40% of ingress packets', 'Global L7 load balancer outage'"
          ),
        ]
      ),
    ]
  )

  public static let ecommerceCatalog50Preset = ScenarioPreset(
    id: "ecommerce_catalog_50",
    title: "Retail Taxonomy (50 Options)",
    subtitle:
      "EG2 Showcase: 50 Product Departments [Choice] + Gift Wrap [Boolean] + Price Tier 1..4 [Score]",
    domainContext:
      "You are an on-device e-commerce catalog classifier routing shopper search queries and product listings into 50 fine-grained retail departments, checking standard gift-wrap eligibility, and estimating the price tier.",
    candidateQueries: [
      "Sony WH-1000XM5 wireless active noise-canceling over-ear Bluetooth headphones with carrying case",
      "DeWalt 20V MAX XR brushless cordless 1/2-inch hammer drill and impact driver combo kit",
      "La Roche-Posay Anthelios Melt-in Milk SPF 60 daily facial and body sunscreen lotion",
      "Breville Barista Express Espresso Machine with integrated conical burr grinder and steam wand",
    ],
    questions: [
      PolymorphicQuestionSpec(
        id: "is_gift_wrap_eligible",
        type: .boolean,
        prompt:
          "Can this item be shipped in standard gift-wrap packaging (compact boxed consumer product vs. heavy appliance/hazardous/bulk)?",
        options: [
          DecisionOptionItem(
            label: "true",
            description:
              "Compact boxed consumer electronics, headphones, skincare, books, apparel, watches, or small kitchen gadgets that fit in standard gift wrap.",
            examples:
              "'Sony WH-1000XM5 wireless Bluetooth headphones', 'La Roche-Posay SPF 60 sunscreen lotion', 'Silk necktie and leather wallet gift set'"
          ),
          DecisionOptionItem(
            label: "false",
            description:
              "Oversized major appliances, heavy furniture, 80-lb concrete bags, flammable paint/gasoline cans, or bulk lumber that cannot be gift-wrapped.",
            examples:
              "'36-inch French door stainless steel refrigerator', 'King-size hybrid innerspring mattress', '5-gallon bucket of exterior acrylic latex paint'"
          ),
        ]
      ),
      PolymorphicQuestionSpec(
        id: "catalog_department",
        type: .choice,
        prompt:
          "Which of the 50 retail catalog departments best matches this product listing or shopper query?",
        options: buildOptions([
          RawOptionSeed(
            label: "smartphones_cases",
            description:
              "Unlocked 5G smartphones, MagSafe protective cases, and tempered glass screen protectors.",
            examples:
              "'iPhone 16 Pro silicone MagSafe case', 'Unlocked Android 5G smartphone 256GB'"),
          RawOptionSeed(
            label: "laptops_tablets",
            description:
              "Ultrabook laptops, gaming notebooks, iPads, Android tablets, and stylus pens.",
            examples: "'14-inch MacBook Pro M4 laptop', '11-inch OLED tablet with digital stylus'"),
          RawOptionSeed(
            label: "headphones_earbuds",
            description:
              "Over-ear noise-canceling Bluetooth headphones, true wireless earbuds, and audiophile IEMs.",
            examples:
              "'Sony WH-1000XM5 wireless active noise-canceling headphones', 'AirPods Pro wireless charging earbuds'"
          ),
          RawOptionSeed(
            label: "cameras_lenses",
            description:
              "Mirrorless digital cameras, prime/zoom lenses, action cameras, gimbals, and tripods.",
            examples:
              "'35mm f/1.4 full-frame mirrorless camera lens', 'Waterproof 4K action camera with stabilizer'"
          ),
          RawOptionSeed(
            label: "tv_home_theater",
            description:
              "4K OLED/QLED televisions, Dolby Atmos soundbars, AV receivers, and home cinema projectors.",
            examples: "'65-inch 4K OLED smart TV', 'Dolby Atmos wireless soundbar with subwoofer'"),
          RawOptionSeed(
            label: "video_games_consoles",
            description:
              "PlayStation, Xbox, and Nintendo consoles, wireless controllers, and physical video games.",
            examples:
              "'DualSense wireless gaming controller', 'Nintendo Switch OLED console bundle'"),
          RawOptionSeed(
            label: "smart_home_automation",
            description:
              "Smart Wi-Fi thermostats, video doorbells, Zigbee hubs, and smart deadbolt locks.",
            examples: "'Smart Wi-Fi learning thermostat', '2K wired video doorbell with chime'"),
          RawOptionSeed(
            label: "wearable_smartwatches",
            description:
              "GPS running smartwatches, heart-rate fitness bands, and smart sleep-tracking rings.",
            examples:
              "'Garmin GPS multisport running watch', 'Titanium smart sleep and recovery ring'"),
          RawOptionSeed(
            label: "pc_components_ssd",
            description:
              "NVMe M.2 internal SSDs, desktop GPUs, DDR5 RAM modules, motherboards, and ATX power supplies.",
            examples:
              "'2TB PCIe Gen4 NVMe M.2 internal SSD', '32GB DDR5 6000MHz desktop memory kit'"),
          RawOptionSeed(
            label: "printers_scanners",
            description:
              "Laser and inkjet all-in-one printers, toner cartridges, and duplex document scanners.",
            examples:
              "'Wireless color laser all-in-one printer', 'High-yield black toner cartridge twin pack'"
          ),
          RawOptionSeed(
            label: "coffee_espresso_makers",
            description:
              "Espresso machines, burr coffee grinders, pour-over kettles, and drip coffee makers.",
            examples:
              "'Breville Barista Express Espresso Machine with burr grinder', 'Gooseneck electric pour-over kettle'"
          ),
          RawOptionSeed(
            label: "blenders_small_appliances",
            description:
              "Countertop air fryers, high-speed blenders, stand mixers, toaster ovens, and rice cookers.",
            examples:
              "'5-quart tilt-head kitchen stand mixer', 'Dual-basket digital countertop air fryer'"),
          RawOptionSeed(
            label: "major_kitchen_appliances",
            description:
              "Full-size refrigerators, dishwashers, induction ranges, and washer/dryer laundry pairs.",
            examples:
              "'36-inch French door stainless steel refrigerator', 'Front-load high-efficiency steam washing machine'"
          ),
          RawOptionSeed(
            label: "cookware_skillets_pots",
            description:
              "Tri-ply stainless steel pans, enameled cast-iron Dutch ovens, and nonstick skillets.",
            examples:
              "'6-quart enameled cast iron Dutch oven', '12-inch tri-ply stainless steel frying pan'"
          ),
          RawOptionSeed(
            label: "bakeware_cake_sheets",
            description:
              "Aluminum half-sheet baking pans, silicone muffin molds, cooling racks, and loaf tins.",
            examples:
              "'Rimmed aluminum half-sheet baking pan set', 'Nonstick 12-cup carbon steel muffin tin'"
          ),
          RawOptionSeed(
            label: "knives_kitchen_cutlery",
            description:
              "Chef knives, santoku blades, knife sharpening whetstones, and end-grain cutting boards.",
            examples:
              "'8-inch forged German stainless steel chef knife', 'Walnut end-grain butcher block cutting board'"
          ),
          RawOptionSeed(
            label: "tableware_dinner_plates",
            description:
              "Porcelain dinnerware sets, crystal wine glasses, flatware forks/spoons, and serving bowls.",
            examples:
              "'16-piece bone china dinnerware service set', 'Lead-free crystal Bordeaux wine glasses'"
          ),
          RawOptionSeed(
            label: "mattresses_bed_pillows",
            description:
              "Memory foam and hybrid mattresses, down pillows, percale sheets, and duvet comforters.",
            examples:
              "'Queen-size cooling gel memory foam mattress', '400-thread-count organic cotton percale sheet set'"
          ),
          RawOptionSeed(
            label: "bath_towels_shower",
            description:
              "Turkish cotton bath towels, plush bathrobes, memory-foam bath mats, and shower curtains.",
            examples:
              "'800 GSM Turkish cotton luxury bath towel set', 'Waffle-weave linen spa bathrobe'"),
          RawOptionSeed(
            label: "living_room_sofas",
            description:
              "Sectional sofas, leather armchairs, coffee tables, TV media consoles, and bookcases.",
            examples:
              "'Mid-century modern velvet 3-seater sofa', 'Solid oak lift-top living room coffee table'"
          ),
          RawOptionSeed(
            label: "office_desks_chairs",
            description:
              "Ergonomic mesh task chairs, motorized sit-stand desks, and monitor arm mounts.",
            examples:
              "'Ergonomic lumbar-support mesh office chair', 'Dual-motor electric standing desk frame'"
          ),
          RawOptionSeed(
            label: "lighting_lamps_bulbs",
            description:
              "Chandeliers, LED floor lamps, dimmable Edison bulbs, and flush-mount ceiling fans.",
            examples:
              "'52-inch brushed nickel ceiling fan with LED light', 'Brass articulated desk reading lamp'"
          ),
          RawOptionSeed(
            label: "rugs_curtains_drapes",
            description:
              "Hand-tufted wool area rugs, blackout thermal window curtains, and bamboo roller shades.",
            examples:
              "'8x10 hand-knotted wool geometric area rug', 'Linen room-darkening blackout curtain panels'"
          ),
          RawOptionSeed(
            label: "closet_storage_bins",
            description:
              "Velvet clothes hangers, modular closet shelving, under-bed storage bins, and shoe racks.",
            examples:
              "'Stackable clear acrylic drop-front shoe boxes', 'Heavy-duty steel wire garage storage rack'"
          ),
          RawOptionSeed(
            label: "vacuums_floor_cleaners",
            description:
              "Cordless stick vacuums, LiDAR robot vacuums, carpet steam cleaners, and spin mops.",
            examples:
              "'Cordless HEPA cyclone stick vacuum cleaner', 'Robot vacuum and mop with self-emptying dock'"
          ),
          RawOptionSeed(
            label: "air_purifiers_hvac",
            description:
              "True HEPA air purifiers, ultrasonic humidifiers, dehumidifiers, and portable AC units.",
            examples:
              "'True HEPA H13 allergens air purifier for large rooms', '50-pint basement dehumidifier with pump'"
          ),
          RawOptionSeed(
            label: "power_tools_drills",
            description:
              "Cordless brushless drills, circular saws, angle grinders, and rotary sanders.",
            examples:
              "'DeWalt 20V MAX brushless hammer drill and impact driver kit', '10-inch sliding compound miter saw'"
          ),
          RawOptionSeed(
            label: "hand_tools_wrenches",
            description:
              "Chrome-vanadium socket wrench sets, ratcheting screwdrivers, pliers, and tape measures.",
            examples:
              "'120-piece mechanics ratchet and socket set', 'Insulated electrician screwdriver kit'"
          ),
          RawOptionSeed(
            label: "plumbing_faucets_sinks",
            description:
              "Pull-down kitchen faucets, dual-flush toilets, rainfall showerheads, and PEX fittings.",
            examples:
              "'Brushed gold pull-down kitchen sink faucet', 'High-pressure filtered rainfall showerhead'"
          ),
          RawOptionSeed(
            label: "electrical_dimmers_wire",
            description:
              "GFCI wall outlets, smart dimmer switches, circuit breakers, and Romex copper wire.",
            examples:
              "'Tamper-resistant 20A GFCI duplex outlet 10-pack', 'In-wall smart Wi-Fi dimmer light switch'"
          ),
          RawOptionSeed(
            label: "paint_primers_brushes",
            description:
              "Interior/exterior latex wall paint, wood stain, angled paintbrushes, and drop cloths.",
            examples:
              "'1-gallon eggshell interior zero-VOC wall paint', '2-inch angled synthetic bristle sash brush'"
          ),
          RawOptionSeed(
            label: "lawn_garden_plants",
            description:
              "Battery lawn mowers, expandable garden hoses, pruning shears, and potting soil.",
            examples:
              "'40V cordless brushless push lawn mower', 'Bypass titanium gardening pruning shears'"
          ),
          RawOptionSeed(
            label: "outdoor_grills_patio",
            description:
              "Propane gas BBQ grills, pellet smokers, patio dining sets, and cantilever umbrellas.",
            examples:
              "'4-burner liquid propane stainless steel gas grill', 'Wood-fired outdoor pizza oven'"
          ),
          RawOptionSeed(
            label: "camping_tents_hiking",
            description:
              "Ultralight backpacking tents, down sleeping bags, trekking poles, and headlamps.",
            examples:
              "'2-person ultralight waterproof backpacking tent', '15-degree goose down mummy sleeping bag'"
          ),
          RawOptionSeed(
            label: "cycling_bikes_helmets",
            description:
              "Road and mountain bicycles, MIPS bike helmets, U-locks, and floor tire pumps.",
            examples:
              "'MIPS road cycling helmet with rear LED', 'Heavy-duty hardened steel bicycle U-lock'"
          ),
          RawOptionSeed(
            label: "fitness_weights_cardio",
            description:
              "Adjustable dumbbells, cast-iron kettlebells, folding treadmills, and rowing machines.",
            examples:
              "'5-to-52.5 lb selectorized adjustable dumbbell pair', '35 lb powder-coated cast iron kettlebell'"
          ),
          RawOptionSeed(
            label: "yoga_racquet_sports",
            description:
              "Non-slip yoga mats, pickleball paddles, tennis racquets, and basketballs.",
            examples:
              "'Carbon-fiber honeycomb core pickleball paddle', '5mm natural rubber non-slip yoga mat'"
          ),
          RawOptionSeed(
            label: "skincare_sunscreen_serums",
            description:
              "Mineral/chemical SPF sunscreens, vitamin C facial serums, retinol creams, and cleansers.",
            examples:
              "'La Roche-Posay Anthelios SPF 60 facial sunscreen lotion', 'Hyaluronic acid hydrating face serum'"
          ),
          RawOptionSeed(
            label: "haircare_shampoo_dryers",
            description:
              "Ionic hair dryers, ceramic flat irons, sulfate-free shampoos, and argan hair oils.",
            examples:
              "'High-velocity ionic hair dryer with diffuser', 'Sulfate-free keratin repair shampoo and conditioner'"
          ),
          RawOptionSeed(
            label: "makeup_fragrance_perfume",
            description:
              "Liquid foundation, matte lipstick, mascara, eyeshadow palettes, and eau de parfum.",
            examples:
              "'Long-wear liquid matte foundation SPF 25', 'Woody bergamot eau de parfum 100ml spray'"
          ),
          RawOptionSeed(
            label: "vitamins_supplements",
            description:
              "Whey protein isolate powder, omega-3 fish oil softgels, magnesium, and daily multivitamins.",
            examples:
              "'Grass-fed chocolate whey protein isolate 2lb', 'Triple-strength Omega-3 fish oil softgels'"
          ),
          RawOptionSeed(
            label: "oral_dental_shaving",
            description:
              "Sonic electric toothbrushes, water flossers, electric foil shavers, and safety razors.",
            examples:
              "'Rechargeable sonic electric toothbrush with pressure sensor', 'Cordless counter water flosser'"
          ),
          RawOptionSeed(
            label: "baby_strollers_carseats",
            description:
              "Convertible infant car seats, lightweight travel strollers, and baby video monitors.",
            examples:
              "'All-in-one convertible rear-and-forward-facing car seat', 'One-hand fold compact travel stroller'"
          ),
          RawOptionSeed(
            label: "diapers_baby_feeding",
            description:
              "Disposable diapers, sensitive baby wipes, anti-colic glass baby bottles, and breast pumps.",
            examples:
              "'Hypoallergenic fragrance-free baby wipes 12-pack', 'Wide-neck glass anti-colic baby bottles'"
          ),
          RawOptionSeed(
            label: "toys_board_games_lego",
            description:
              "Interlocking building block sets, strategy board games, RC cars, and jigsaw puzzles.",
            examples:
              "'1,500-piece architectural scale building block set', 'Cooperative family strategy board game'"
          ),
          RawOptionSeed(
            label: "arts_crafts_painting",
            description:
              "Watercolor paint sets, stretched cotton canvases, sewing machines, and knitting yarn.",
            examples:
              "'24-pan artist grade watercolor paint palette', 'Heavy-duty computerized quilting sewing machine'"
          ),
          RawOptionSeed(
            label: "musical_guitars_audio",
            description:
              "Acoustic/electric guitars, 88-key digital pianos, XLR condenser microphones, and audio interfaces.",
            examples:
              "'88-key weighted hammer-action digital piano', 'Cardioid XLR studio condenser microphone'"
          ),
          RawOptionSeed(
            label: "pet_dog_cat_supplies",
            description:
              "Grain-free dry dog kibble, clumping cat litter, orthopedic pet beds, and no-pull harnesses.",
            examples:
              "'Salmon and sweet potato grain-free adult dog food 30lb', 'Self-cleaning automatic litter box'"
          ),
          RawOptionSeed(
            label: "automotive_car_accessories",
            description:
              "4K dashcams, portable lithium jump starters, synthetic motor oil, and wiper blades.",
            examples:
              "'Front and rear 4K GPS dashcam with parking mode', '1500A lithium car battery jump starter'"
          ),
          RawOptionSeed(
            label: "pantry_olive_oil_grocery",
            description:
              "Extra-virgin olive oil, whole-bean specialty coffee, organic honey, and pantry spices.",
            examples:
              "'Organic cold-pressed extra virgin olive oil 1-liter', 'Single-origin Ethiopian whole bean coffee'"
          ),
        ])
      ),
      PolymorphicQuestionSpec(
        id: "price_tier",
        type: .score,
        prompt:
          "Estimate the retail price tier of this item from 1 (Everyday <$30) to 4 (Premium/Pro >$250).",
        options: [
          DecisionOptionItem(
            label: "1",
            description:
              "Tier 1 (Everyday Essential <$30): Skincare lotion, olive oil, hand tools, or pantry items.",
            examples: "'La Roche-Posay SPF 60 sunscreen', '1-liter extra virgin olive oil'"
          ),
          DecisionOptionItem(
            label: "2",
            description:
              "Tier 2 (Mid-Range $30–$100): Cookware, cycling helmets, or small accessories.",
            examples: "'MIPS cycling helmet', '8-inch forged chef knife'"
          ),
          DecisionOptionItem(
            label: "3",
            description:
              "Tier 3 (High-End $100–$250): Power tool combo kits, AirPods, or small appliances.",
            examples:
              "'DeWalt 20V brushless drill and impact driver kit', 'Countertop dual air fryer'"
          ),
          DecisionOptionItem(
            label: "4",
            description:
              "Tier 4 (Flagship / Pro >$250): Flagship ANC headphones, espresso machines, or laptops.",
            examples:
              "'Sony WH-1000XM5 wireless headphones', 'Breville Barista Express Espresso Machine'"
          ),
        ]
      ),
    ]
  )

  public static let assistantIntents100Preset = ScenarioPreset(
    id: "assistant_intents_100",
    title: "OS Action Router (100 Options)",
    subtitle:
      "EG2 Showcase: 100 Device Intents [Choice] + Biometric Auth [Boolean] + Urgency 1..3 [Score]",
    domainContext:
      "You are an on-device mobile OS assistant routing spoken or typed user commands across 100 fine-grained system actions and app intents in a single prewarmed embedding pass, while checking if biometric authentication is required.",
    candidateQueries: [
      "Wire $2,500 from my checking account to my landlord's routing number ending in 4819.",
      "Set a 15-minute pasta timer and turn on Do Not Disturb until 9 PM.",
      "Navigate home avoiding toll roads and share my live ETA with Mom.",
      "Export my last 30 days of ECG heart rate variability and sleep stages as a PDF for my cardiologist.",
    ],
    questions: [
      PolymorphicQuestionSpec(
        id: "requires_biometric_auth",
        type: .boolean,
        prompt:
          "Does executing this command require FaceID / fingerprint biometric confirmation (financial transfer, security lock, passwords, or private medical export)?",
        options: [
          DecisionOptionItem(
            label: "true",
            description:
              "Sensitive action moving money, wiring funds, unlocking physical doors, viewing saved passwords, or exporting private medical/health records.",
            examples:
              "'Wire $2,500 from my checking account to my landlord', 'Unlock the front door smart deadbolt', 'Export my ECG and medical records as a PDF'"
          ),
          DecisionOptionItem(
            label: "false",
            description:
              "Routine hands-free convenience command such as setting a timer, playing music, checking weather, or starting turn-by-turn navigation.",
            examples:
              "'Set a 15-minute pasta timer', 'Navigate home avoiding toll roads', 'Play upbeat jazz in the kitchen'"
          ),
        ]
      ),
      PolymorphicQuestionSpec(
        id: "system_intent",
        type: .choice,
        prompt:
          "Which of the 100 fine-grained OS system intents should be invoked for this user command?",
        options: buildOptions([
          // 1-10: Clock & Scheduling
          RawOptionSeed(
            label: "timer_set",
            description: "Start, pause, or cancel a countdown kitchen or workout timer.",
            examples: "'Set a 15-minute pasta timer', 'Start a 45-second plank timer'"),
          RawOptionSeed(
            label: "alarm_create", description: "Set or toggle a morning wake-up alarm clock.",
            examples: "'Wake me up tomorrow at 6:30 AM', 'Turn off my weekday 7 AM alarm'"),
          RawOptionSeed(
            label: "stopwatch_control", description: "Start, lap, or reset the stopwatch.",
            examples: "'Start the stopwatch now', 'Record a lap time on the stopwatch'"),
          RawOptionSeed(
            label: "world_clock_query",
            description: "Check current local time in another city or time zone.",
            examples:
              "'What time is it in Tokyo right now?', 'Time difference between London and New York'"
          ),
          RawOptionSeed(
            label: "calendar_create_event",
            description: "Schedule a new calendar meeting or appointment.",
            examples:
              "'Schedule a dentist appointment for Tuesday at 2 PM', 'Add team lunch to my calendar Friday at noon'"
          ),
          RawOptionSeed(
            label: "calendar_reschedule",
            description: "Move or change the time of an existing calendar meeting.",
            examples:
              "'Move my 3 PM 1:1 meeting to 4:30 PM', 'Push tomorrow standup back by 30 minutes'"),
          RawOptionSeed(
            label: "reminder_add_location",
            description: "Create a geofenced or time-based reminder task.",
            examples:
              "'Remind me to buy milk when I leave the office', 'Remind me at 8 PM to take out the recycling'"
          ),
          RawOptionSeed(
            label: "reminder_list_today",
            description: "Read out today's pending reminders and to-do items.",
            examples: "'What reminders do I have due today?', 'Show my overdue to-do list'"),
          RawOptionSeed(
            label: "pomodoro_focus_session",
            description: "Start a 25-minute deep-work Pomodoro focus timer.",
            examples:
              "'Start a 25-minute Pomodoro focus block', 'Begin deep work session and block distractions'"
          ),
          RawOptionSeed(
            label: "out_of_office_auto_reply",
            description: "Configure vacation out-of-office auto-responder.",
            examples:
              "'Turn on my out-of-office vacation reply until Monday', 'Set auto-reply that I am on parental leave'"
          ),
          // 11-20: Device Settings & Connectivity
          RawOptionSeed(
            label: "wifi_toggle",
            description: "Turn Wi-Fi networking on, off, or switch wireless networks.",
            examples: "'Turn off Wi-Fi and reconnect', 'Connect to the guest Wi-Fi network'"),
          RawOptionSeed(
            label: "bluetooth_pair",
            description: "Pair or connect Bluetooth headphones, speakers, or car audio.",
            examples: "'Connect Bluetooth to my Sony headphones', 'Put Bluetooth in pairing mode'"),
          RawOptionSeed(
            label: "hotspot_enable",
            description: "Turn on personal cellular Wi-Fi hotspot tethering.",
            examples:
              "'Turn on my personal Wi-Fi hotspot for my laptop', 'Disable cellular tethering'"),
          RawOptionSeed(
            label: "airplane_mode_toggle",
            description: "Enable or disable Airplane Mode for flights.",
            examples: "'Turn on airplane mode for takeoff', 'Disable airplane mode'"),
          RawOptionSeed(
            label: "do_not_disturb_schedule",
            description: "Silence notifications with Do Not Disturb mode.",
            examples: "'Turn on Do Not Disturb until 9 PM', 'Silence all calls for the next hour'"),
          RawOptionSeed(
            label: "screen_brightness_adjust",
            description: "Increase or dim display screen brightness and True Tone.",
            examples: "'Set screen brightness to 80%', 'Dim the display to minimum brightness'"),
          RawOptionSeed(
            label: "dark_mode_toggle",
            description: "Switch system UI appearance between Dark Mode and Light Mode.",
            examples: "'Switch my phone to dark mode', 'Turn on light theme appearance'"),
          RawOptionSeed(
            label: "battery_saver_enable",
            description: "Turn on Low Power Mode to conserve battery life.",
            examples: "'Enable low power battery saver mode', 'Turn on extreme battery saver'"),
          RawOptionSeed(
            label: "volume_media_ringtone",
            description: "Adjust media speaker volume or ringtone level.",
            examples: "'Turn media volume up to 70%', 'Mute the ringer volume'"),
          RawOptionSeed(
            label: "flashlight_toggle",
            description: "Turn the rear camera LED flashlight torch on or off.",
            examples: "'Turn on the flashlight', 'Switch off the torch light'"),
          // 21-30: Messaging & Calls
          RawOptionSeed(
            label: "sms_send_text",
            description: "Send an SMS or iMessage/RCS text message to a contact.",
            examples:
              "'Text Sarah I am running 5 minutes late', 'Send a message to Dad saying happy birthday'"
          ),
          RawOptionSeed(
            label: "sms_read_unread", description: "Read aloud the latest unread text messages.",
            examples: "'Read my unread text messages', 'What did Alex just text me?'"),
          RawOptionSeed(
            label: "phone_call_dial",
            description: "Place a voice phone call on speakerphone or handset.",
            examples: "'Call Mom on speakerphone', 'Dial Dr. Patel office number'"),
          RawOptionSeed(
            label: "voicemail_play", description: "Listen to new visual voicemail recordings.",
            examples: "'Play my latest voicemail message', 'Check new voicemails from today'"),
          RawOptionSeed(
            label: "video_call_start",
            description: "Start a FaceTime, Meet, or WhatsApp video call.",
            examples: "'Start a FaceTime video call with Maya', 'Video call the family group'"),
          RawOptionSeed(
            label: "email_compose_send", description: "Draft and send a new email message.",
            examples:
              "'Send an email to Jordan with subject Q4 Budget', 'Email my accountant the tax receipt'"
          ),
          RawOptionSeed(
            label: "email_search_attachments",
            description: "Search inbox emails for PDF attachments or receipts.",
            examples:
              "'Find emails from United Airlines with PDF attachments', 'Search my inbox for the signed lease'"
          ),
          RawOptionSeed(
            label: "chat_mute_thread",
            description: "Mute notifications from a noisy group chat thread.",
            examples:
              "'Mute the neighborhood group chat for 8 hours', 'Silence notifications from the fantasy football thread'"
          ),
          RawOptionSeed(
            label: "contact_create_update",
            description: "Save a new phone number or address to Contacts.",
            examples:
              "'Add Sam Chen to my contacts with number 555-0192', 'Update Mom home address in contacts'"
          ),
          RawOptionSeed(
            label: "emergency_sos_broadcast",
            description: "Trigger emergency SOS alert and share GPS coordinates.",
            examples:
              "'Call emergency services and send my GPS location', 'Trigger SOS safety check-in'"),
          // 31-40: Maps & Mobility
          RawOptionSeed(
            label: "nav_route_destination",
            description: "Start turn-by-turn GPS driving or walking directions.",
            examples:
              "'Give me driving directions to SFO Terminal 2', 'Walking directions to the nearest library'"
          ),
          RawOptionSeed(
            label: "nav_avoid_tolls_highways",
            description: "Route navigation while avoiding toll roads or ferries.",
            examples:
              "'Navigate home avoiding toll roads and share my live ETA', 'Route to Boston without highways'"
          ),
          RawOptionSeed(
            label: "nav_share_live_eta",
            description: "Share live arrival time and route progress with a contact.",
            examples: "'Share my live driving ETA with Mom', 'Send my arrival time to my wife'"),
          RawOptionSeed(
            label: "nav_find_ev_charger",
            description: "Locate nearby fast EV charging stations along the route.",
            examples:
              "'Find a 250kW CCS fast EV charger along my route', 'Show available Superchargers within 5 miles'"
          ),
          RawOptionSeed(
            label: "nav_find_parking_garage",
            description: "Search for parking garages or drop a parked-car pin.",
            examples:
              "'Find covered parking near the concert hall', 'Remember where I parked my car on Level 3'"
          ),
          RawOptionSeed(
            label: "transit_train_bus_schedule",
            description: "Check live subway, commuter rail, or bus departures.",
            examples:
              "'When does the next northbound Caltrain leave?', 'Check Muni bus arrival times at Market Street'"
          ),
          RawOptionSeed(
            label: "rideshare_book_pickup",
            description: "Request an Uber, Lyft, or Waymo autonomous ride.",
            examples: "'Book an UberXL to the airport right now', 'Call a Waymo ride to downtown'"),
          RawOptionSeed(
            label: "flight_status_gate_check",
            description: "Check live airline flight departure gate and delays.",
            examples:
              "'Is flight UA 892 on time and what gate is it at?', 'Check baggage claim carousel for Delta 405'"
          ),
          RawOptionSeed(
            label: "hotel_reservation_lookup",
            description: "Retrieve hotel check-in confirmation and room address.",
            examples:
              "'Show my Marriott hotel reservation confirmation number', 'What time is check-in for my hotel in Seattle?'"
          ),
          RawOptionSeed(
            label: "weather_hourly_radar",
            description: "Check hourly rain forecast, UV index, and wind speed.",
            examples:
              "'Will it rain in San Francisco this afternoon?', 'What is the UV index and high temperature today?'"
          ),
          // 41-50: Media, Camera & Photos
          RawOptionSeed(
            label: "music_play_artist_playlist",
            description: "Play songs, albums, or playlists on Spotify/Apple Music.",
            examples: "'Play my 90s acoustic rock playlist', 'Shuffle songs by Miles Davis'"),
          RawOptionSeed(
            label: "podcast_resume_episode",
            description: "Resume playing the latest podcast episode.",
            examples:
              "'Resume my latest podcast episode where I left off', 'Skip forward 30 seconds in this podcast'"
          ),
          RawOptionSeed(
            label: "audiobook_sleep_timer",
            description: "Play an audiobook and set a stop-playback sleep timer.",
            examples:
              "'Read my sci-fi audiobook and stop in 20 minutes', 'Set a sleep timer for the end of this chapter'"
          ),
          RawOptionSeed(
            label: "tv_cast_screen_mirror",
            description: "Cast video or mirror screen to a living room TV or AirPlay speaker.",
            examples:
              "'Cast this video to the living room Apple TV', 'Transfer music playback to the kitchen speaker'"
          ),
          RawOptionSeed(
            label: "camera_selfie_portrait",
            description: "Open the front camera in Portrait mode with a 3-second shutter timer.",
            examples:
              "'Take a portrait mode selfie with a 3-second timer', 'Open the camera for a group photo'"
          ),
          RawOptionSeed(
            label: "camera_scan_qr_document",
            description: "Scan a QR code or multi-page paper receipt into PDF.",
            examples:
              "'Scan this restaurant QR code menu', 'Scan this paper receipt into a PDF document'"),
          RawOptionSeed(
            label: "video_record_slow_motion",
            description: "Record 4K cinematic or 240fps slow-motion video.",
            examples:
              "'Start recording a slow-motion video', 'Record 4K 60fps video with action stabilization'"
          ),
          RawOptionSeed(
            label: "photos_search_memories",
            description: "Search photo library by trip location, pet, or date.",
            examples:
              "'Show photos of my golden retriever at the beach last summer', 'Find pictures from our trip to Kyoto'"
          ),
          RawOptionSeed(
            label: "photos_remove_background",
            description: "Erase photobombers or isolate the subject background.",
            examples:
              "'Remove the background from this product photo', 'Erase the tourist in the background of this picture'"
          ),
          RawOptionSeed(
            label: "voice_memo_transcribe",
            description: "Record an audio voice memo with live speech transcription.",
            examples:
              "'Start recording a voice memo for lecture notes', 'Transcribe my latest audio recording'"
          ),
          // 51-60: Smart Home & IoT
          RawOptionSeed(
            label: "smart_lights_dim_color",
            description: "Turn on, dim, or change color of smart home light bulbs.",
            examples:
              "'Dim the living room lights to 30% warm white', 'Turn off all downstairs bedroom lights'"
          ),
          RawOptionSeed(
            label: "thermostat_set_temperature",
            description: "Set smart home HVAC heating or air conditioning target temperature.",
            examples:
              "'Set the house thermostat to 70 degrees', 'Turn on cool mode in the upstairs bedroom'"
          ),
          RawOptionSeed(
            label: "smart_lock_door_control",
            description: "Lock or unlock the front door smart deadbolt.",
            examples: "'Lock the front door deadbolt', 'Check if the back patio door is locked'"),
          RawOptionSeed(
            label: "garage_door_open_close",
            description: "Open or close the motorized garage door opener.",
            examples: "'Close the main garage door', 'Open the garage door for delivery'"),
          RawOptionSeed(
            label: "security_camera_live_feed",
            description: "View live video feed from the front porch or nursery camera.",
            examples:
              "'Show the front porch security camera live view', 'Check the baby nursery camera feed'"
          ),
          RawOptionSeed(
            label: "robot_vacuum_start_room",
            description: "Send the robot vacuum to clean a specific room.",
            examples:
              "'Tell the robot vacuum to vacuum the kitchen crumbs', 'Dock the robot vacuum to charge'"
          ),
          RawOptionSeed(
            label: "smart_plug_appliance_power",
            description: "Toggle power on a smart outlet plug (coffee maker, fan).",
            examples:
              "'Turn on the espresso machine smart plug', 'Switch off the patio string lights plug'"
          ),
          RawOptionSeed(
            label: "window_blinds_shade_position",
            description: "Raise or lower motorized window shades and blinds.",
            examples:
              "'Lower the motorized blackout blinds in the bedroom', 'Open the living room window shades halfway'"
          ),
          RawOptionSeed(
            label: "garden_sprinkler_zone_run", description: "Run lawn irrigation sprinkler zones.",
            examples:
              "'Run the front yard lawn sprinklers for 10 minutes', 'Skip tonight irrigation schedule due to rain'"
          ),
          RawOptionSeed(
            label: "home_alarm_arm_away",
            description: "Arm the home security alarm system in Away or Stay mode.",
            examples:
              "'Arm the home security system in Away mode', 'Set the house alarm to Night Stay mode'"
          ),
          // 61-70: Banking, Payments & Finance
          RawOptionSeed(
            label: "bank_wire_ach_transfer",
            description: "Wire funds or send an ACH bank transfer between accounts.",
            examples:
              "'Wire $2,500 from my checking account to my landlord routing number 4819', 'Transfer $500 from checking to high-yield savings'"
          ),
          RawOptionSeed(
            label: "peer_payment_send_money",
            description: "Send or request peer-to-peer cash (Venmo, Zelle, CashApp).",
            examples:
              "'Send $28 to Priya for dinner pizza', 'Request $45 from Chris for concert tickets'"),
          RawOptionSeed(
            label: "bill_pay_utility_card",
            description: "Pay monthly credit card statement or electric utility bill.",
            examples:
              "'Pay my full Visa credit card statement balance today', 'Schedule my electric utility bill payment'"
          ),
          RawOptionSeed(
            label: "card_freeze_lock_lost",
            description: "Instantly freeze or lock a misplaced debit or credit card.",
            examples:
              "'Freeze my sapphire credit card immediately, I cannot find my wallet', 'Lock my debit card from international transactions'"
          ),
          RawOptionSeed(
            label: "account_balance_checking",
            description: "Check available checking and savings account balances.",
            examples:
              "'What is my current checking account balance?', 'How much money is in my emergency savings account?'"
          ),
          RawOptionSeed(
            label: "spending_category_analytics",
            description: "Break down monthly spending on dining, groceries, or travel.",
            examples:
              "'How much did I spend on restaurants and coffee this month?', 'Show my recurring subscription charges'"
          ),
          RawOptionSeed(
            label: "stock_portfolio_quote",
            description: "Check live stock ticker prices and portfolio gain/loss.",
            examples:
              "'How is Alphabet stock trading today?', 'Show my brokerage portfolio performance this week'"
          ),
          RawOptionSeed(
            label: "crypto_wallet_swap", description: "Check hardware wallet balances or gas fees.",
            examples:
              "'Check my Ethereum hardware wallet balance', 'What are current network gas fees?'"),
          RawOptionSeed(
            label: "tax_document_1099_download",
            description: "Download annual 1099 or W-2 tax statements.",
            examples:
              "'Download my 2025 1099-DIV tax form PDF', 'Find my annual mortgage interest tax statement'"
          ),
          RawOptionSeed(
            label: "currency_exchange_calculator",
            description: "Convert foreign currency amounts at live exchange rates.",
            examples:
              "'How much is 15,000 Japanese Yen in US Dollars?', 'Convert 250 Euros to British Pounds'"
          ),
          // 71-80: Shopping, Food & Errands
          RawOptionSeed(
            label: "grocery_list_add_item",
            description: "Add food ingredients to the shared family grocery list.",
            examples:
              "'Add avocados and oat milk to the grocery list', 'Put dish soap on my shopping list'"
          ),
          RawOptionSeed(
            label: "restaurant_table_reserve",
            description: "Book a dinner table reservation on OpenTable/Resy.",
            examples:
              "'Book a table for 4 at an Italian restaurant Friday at 7 PM', 'Reserve patio seating for 2 tonight'"
          ),
          RawOptionSeed(
            label: "food_delivery_reorder",
            description: "Reorder a favorite meal for delivery (DoorDash, UberEats).",
            examples:
              "'Reorder my usual pad thai dinner from Thai Basil', 'Order two margherita pizzas for delivery'"
          ),
          RawOptionSeed(
            label: "coffee_shop_mobile_pickup",
            description: "Place a mobile order-ahead iced latte for pickup.",
            examples:
              "'Order an iced oat milk latte for pickup at Blue Bottle', 'Mobile order my morning cold brew'"
          ),
          RawOptionSeed(
            label: "package_delivery_tracking",
            description: "Track live UPS/FedEx/USPS parcel delivery status.",
            examples:
              "'Where is my Amazon package arriving today?', 'Track my FedEx shipment delivery window'"
          ),
          RawOptionSeed(
            label: "return_label_qr_generate",
            description: "Start an online item return and pull up the drop-off QR code.",
            examples:
              "'Generate a return QR code for the running shoes I bought', 'Start a refund return for order #8042'"
          ),
          RawOptionSeed(
            label: "price_drop_alert_watch",
            description: "Track a product for price drops or back-in-stock alerts.",
            examples:
              "'Notify me when these headphones drop below $280', 'Set a restock alert for the 2TB SSD'"
          ),
          RawOptionSeed(
            label: "coupon_promo_code_apply",
            description: "Search for valid checkout promo codes and cashback offers.",
            examples:
              "'Find a working discount promo code for Nike checkout', 'Activate 5% cashback offer on groceries'"
          ),
          RawOptionSeed(
            label: "pharmacy_prescription_refill",
            description: "Refill a pharmacy prescription for pickup.",
            examples:
              "'Refill my allergy prescription #44910 at Walgreens', 'Check if my antibiotic prescription is ready for pickup'"
          ),
          RawOptionSeed(
            label: "loyalty_rewards_barcode",
            description: "Open store loyalty member card or boarding pass barcode.",
            examples:
              "'Pull up my supermarket member loyalty barcode', 'Show my gym membership QR scan pass'"
          ),
          // 81-90: Health, Fitness & Medical
          RawOptionSeed(
            label: "workout_run_cycle_start",
            description: "Start tracking an outdoor GPS run, cycling, or swim workout.",
            examples: "'Start a 5K outdoor run workout', 'Track an indoor lap swim session'"),
          RawOptionSeed(
            label: "heart_rate_ecg_export",
            description: "Export ECG heart rate variability and sleep PDF reports for a doctor.",
            examples:
              "'Export my last 30 days of ECG heart rate variability and sleep stages as a PDF', 'Show my resting heart rate trend'"
          ),
          RawOptionSeed(
            label: "sleep_stages_report",
            description: "View last night's REM, deep sleep, and sleep duration score.",
            examples:
              "'How much deep and REM sleep did I get last night?', 'Show my weekly sleep consistency score'"
          ),
          RawOptionSeed(
            label: "water_hydration_log",
            description: "Log glasses or ounces of water drunk today.",
            examples:
              "'Log 16 ounces of water in my hydration tracker', 'How much water have I had today?'"
          ),
          RawOptionSeed(
            label: "meal_calorie_macro_log",
            description: "Log meal calories, protein, and nutrition macros.",
            examples:
              "'Log a grilled chicken salad with 42 grams of protein', 'How many calories do I have left today?'"
          ),
          RawOptionSeed(
            label: "medication_dose_reminder",
            description: "Mark daily vitamins or prescription medication as taken.",
            examples:
              "'Mark my morning blood pressure pill as taken', 'Did I log my evening vitamin D dose?'"
          ),
          RawOptionSeed(
            label: "telehealth_doctor_book",
            description: "Schedule a video telehealth visit or message a physician.",
            examples:
              "'Book a telehealth video appointment with a dermatologist', 'Send a message to my primary care doctor'"
          ),
          RawOptionSeed(
            label: "blood_glucose_pressure_log",
            description: "Record blood pressure cuff reading or continuous glucose level.",
            examples:
              "'Log blood pressure reading 118 over 76', 'What is my current continuous glucose monitor reading?'"
          ),
          RawOptionSeed(
            label: "guided_meditation_breathe",
            description: "Start a 5-minute guided mindfulness breathing exercise.",
            examples:
              "'Start a 5-minute box breathing meditation', 'Play rain sounds for relaxation'"),
          RawOptionSeed(
            label: "medical_id_allergy_card",
            description: "Display emergency Medical ID allergies, blood type, and contacts.",
            examples:
              "'Show my emergency Medical ID card and penicillin allergy', 'Open my emergency health profile'"
          ),
          // 91-100: Productivity, Files & Developer Tools
          RawOptionSeed(
            label: "notes_create_checklist", description: "Create a new note or packing checklist.",
            examples:
              "'Create a camping trip packing checklist in Notes', 'Jot down a quick note about book recommendations'"
          ),
          RawOptionSeed(
            label: "document_pdf_sign_export",
            description: "Apply saved digital signature to a PDF contract.",
            examples:
              "'Sign the bottom of this NDA PDF and save a copy', 'Fill and sign the school permission form'"
          ),
          RawOptionSeed(
            label: "cloud_drive_share_link",
            description: "Grant view/edit access and copy a Google Drive or iCloud file link.",
            examples:
              "'Share the Q4 roadmap presentation with view access', 'Copy a shareable link to the project folder'"
          ),
          RawOptionSeed(
            label: "password_manager_autofill",
            description: "Look up or generate a strong passkey/password in the vault.",
            examples:
              "'Generate a 24-character random password and save it', 'Look up my Wi-Fi router admin password'"
          ),
          RawOptionSeed(
            label: "vpn_tunnel_connect",
            description: "Connect or disconnect the corporate WireGuard/IPsec VPN tunnel.",
            examples: "'Connect to the corporate engineering VPN', 'Disconnect the VPN tunnel'"),
          RawOptionSeed(
            label: "translator_live_conversation",
            description: "Start real-time bilingual speech translation.",
            examples:
              "'Translate this conversation between English and Japanese', 'How do I say where is the train station in Italian?'"
          ),
          RawOptionSeed(
            label: "unit_measurement_convert",
            description: "Convert cooking cups/grams or metric/imperial units.",
            examples: "'Convert 350 grams of flour to cups', 'How many kilometers is 26.2 miles?'"),
          RawOptionSeed(
            label: "calculator_tip_split",
            description: "Calculate restaurant tip percentage and split the bill evenly.",
            examples:
              "'What is a 20% tip on $184 split 4 ways?', 'Divide $126.50 evenly among 3 people'"),
          RawOptionSeed(
            label: "ssh_terminal_server_check",
            description: "Open an SSH terminal session to check server uptime.",
            examples:
              "'SSH into prod-bastion-01 and check uptime', 'Open terminal session to my dev workstation'"
          ),
          RawOptionSeed(
            label: "git_pull_request_approve",
            description: "Check CI status and approve a GitHub pull request.",
            examples:
              "'Approve pull request #412 once CI checks pass', 'Show open pull requests assigned to me for review'"
          ),
        ])
      ),
      PolymorphicQuestionSpec(
        id: "execution_urgency",
        type: .score,
        prompt:
          "Rate the time-sensitivity of executing this command from 1 (Background/Flexible) to 3 (Immediate Real-Time).",
        options: [
          DecisionOptionItem(
            label: "1",
            description:
              "Level 1 (Background / Asynchronous): Exporting monthly PDF reports, tax forms, or price alerts.",
            examples:
              "'Export my last 30 days of ECG and sleep stages as a PDF', 'Download my 1099 tax form'"
          ),
          DecisionOptionItem(
            label: "2",
            description:
              "Level 2 (Interactive Standard): Financial wire transfers, scheduling meetings, or shopping lists.",
            examples:
              "'Wire $2,500 from my checking account to my landlord', 'Add avocados to the grocery list'"
          ),
          DecisionOptionItem(
            label: "3",
            description:
              "Level 3 (Immediate Real-Time): Live turn-by-turn navigation, active kitchen timers, or emergency SOS.",
            examples:
              "'Set a 15-minute pasta timer', 'Navigate home avoiding toll roads and share my live ETA'"
          ),
        ]
      ),
    ]
  )
}

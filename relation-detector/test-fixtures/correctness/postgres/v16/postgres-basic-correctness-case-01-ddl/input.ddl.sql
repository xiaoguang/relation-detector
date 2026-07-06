-- Generated from PostgreSQL catalog for postgres-basic-correctness-case-01.
-- Refresh with PostgresBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_01.auth_permission
CREATE TABLE case_01.auth_permission (
  id integer DEFAULT nextval('auth_permission_id_seq'::regclass) NOT NULL,
  name character varying(255) NOT NULL,
  content_type_id integer NOT NULL,
  codename character varying(100) NOT NULL,
  CONSTRAINT auth_permission_pkey PRIMARY KEY (id),
  CONSTRAINT auth_permission_content_type_id_codename_key UNIQUE (content_type_id, codename)
);
CREATE INDEX auth_permission_37ef4eb4 ON case_01.auth_permission USING btree (content_type_id);
CREATE INDEX auth_permission_417f1b1c ON case_01.auth_permission USING btree (content_type_id);

-- relation-detector-fixture-table: case_01.bad_precompute
CREATE TABLE case_01.bad_precompute (
  id bigint DEFAULT nextval('bad_precompute_id_seq'::regclass) NOT NULL,
  urs_taxid text NOT NULL,
  CONSTRAINT bad_precompute_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.blog
CREATE TABLE case_01.blog (
  id bigint DEFAULT nextval('blog_id_seq'::regclass) NOT NULL,
  title character varying(1000) NOT NULL,
  content text NOT NULL,
  created date DEFAULT ('now'::text)::date,
  featured boolean DEFAULT false,
  release_image character varying(255),
  CONSTRAINT blog_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.cpat_results
CREATE TABLE case_01.cpat_results (
  urs_taxid text NOT NULL,
  fickett_score double precision NOT NULL,
  hexamer_score double precision NOT NULL,
  coding_probability double precision NOT NULL,
  is_protein_coding boolean NOT NULL,
  CONSTRAINT rnc_cpat_results_pkey PRIMARY KEY (urs_taxid),
  CONSTRAINT fk_rnc_cpat_results__urs_taxid FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id)
);

-- relation-detector-fixture-table: case_01.ensembl_assembly
CREATE TABLE case_01.ensembl_assembly (
  assembly_id character varying(255) NOT NULL,
  assembly_full_name character varying(255) NOT NULL,
  gca_accession character varying(20),
  assembly_ucsc character varying(100),
  common_name character varying(255),
  taxid integer NOT NULL,
  ensembl_url character varying(100),
  division character varying(20),
  blat_mapping integer,
  example_chromosome character varying(40),
  example_end integer,
  example_start integer,
  subdomain character varying(100) NOT NULL,
  selected_genome boolean DEFAULT false NOT NULL,
  CONSTRAINT ensembl_assembly_pkey PRIMARY KEY (assembly_id)
);
CREATE INDEX ensembl_assembly_assembly_full_name_4e04e34c934d828c_like ON case_01.ensembl_assembly USING btree (assembly_full_name varchar_pattern_ops);
CREATE INDEX ensembl_assembly_assembly_id_445613b5d4415e25_like ON case_01.ensembl_assembly USING btree (assembly_id varchar_pattern_ops);
CREATE INDEX ensembl_assembly_assembly_ucsc_18c1678ec4499598_like ON case_01.ensembl_assembly USING btree (assembly_ucsc varchar_pattern_ops);
CREATE INDEX ensembl_assembly_b0b7b698 ON case_01.ensembl_assembly USING btree (taxid);
CREATE INDEX ensembl_assembly_common_name_2fb95f30e1a6510c_like ON case_01.ensembl_assembly USING btree (common_name varchar_pattern_ops);
CREATE INDEX ensembl_assembly_d3a9bcdf ON case_01.ensembl_assembly USING btree (assembly_ucsc);
CREATE INDEX ensembl_assembly_f0a6a773 ON case_01.ensembl_assembly USING btree (common_name);
CREATE INDEX ensembl_assembly_f18d9711 ON case_01.ensembl_assembly USING btree (gca_accession);
CREATE INDEX ensembl_assembly_fce94082 ON case_01.ensembl_assembly USING btree (assembly_full_name);
CREATE INDEX ensembl_assembly_gca_accession_6453f4761e62fac1_like ON case_01.ensembl_assembly USING btree (gca_accession varchar_pattern_ops);
CREATE INDEX ensembl_assembly_subdomain_15320e5d ON case_01.ensembl_assembly USING btree (subdomain);
CREATE INDEX ensembl_assembly_subdomain_15320e5d_like ON case_01.ensembl_assembly USING btree (subdomain varchar_pattern_ops);

-- relation-detector-fixture-table: case_01.ensembl_compara
CREATE TABLE case_01.ensembl_compara (
  id integer DEFAULT nextval('ensembl_compara_id_seq'::regclass) NOT NULL,
  ensembl_transcript_id text NOT NULL,
  urs_taxid text NOT NULL,
  homology_id integer NOT NULL,
  CONSTRAINT ensembl_compara_urs_taxid_fkey FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id)
);
CREATE INDEX fk_ensembl_compara__urs_taxid ON case_01.ensembl_compara USING btree (urs_taxid);
CREATE INDEX ix_ensembl_compara__homology_id ON case_01.ensembl_compara USING btree (homology_id);

-- relation-detector-fixture-table: case_01.ensembl_coordinate_systems
CREATE TABLE case_01.ensembl_coordinate_systems (
  id integer DEFAULT nextval('ensembl_coordinate_systems_id_seq'::regclass) NOT NULL,
  chromosome character varying(100) NOT NULL,
  assembly_id character varying(255) NOT NULL,
  coordinate_system text NOT NULL,
  is_reference boolean NOT NULL,
  karyotype_rank integer,
  CONSTRAINT ensembl_coordinate_systems_pkey PRIMARY KEY (id),
  CONSTRAINT ensembl_coordinate_systems_chromosome_assembly_id_key UNIQUE (chromosome, assembly_id),
  CONSTRAINT ensembl_coordinate_systems_assembly_id_fkey FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE
);
CREATE INDEX ix_ensembl_coordinate_systems__assembly_id_chromosome ON case_01.ensembl_coordinate_systems USING btree (assembly_id, chromosome);

-- relation-detector-fixture-table: case_01.ensembl_import_tracking
CREATE TABLE case_01.ensembl_import_tracking (
  id integer DEFAULT nextval('ensembl_import_tracking_id_seq'::regclass) NOT NULL,
  database_name text NOT NULL,
  task_name text NOT NULL,
  was_imported boolean,
  CONSTRAINT ensembl_import_tracking_pkey PRIMARY KEY (id),
  CONSTRAINT ensembl_import_tracking_database_name_task_name_key UNIQUE (database_name, task_name)
);

-- relation-detector-fixture-table: case_01.ensembl_pseudogene_exons
CREATE TABLE case_01.ensembl_pseudogene_exons (
  id bigint DEFAULT nextval('ensembl_pseudogene_exons_id_seq'::regclass) NOT NULL,
  region_id integer,
  exon_start integer NOT NULL,
  exon_stop integer NOT NULL,
  CONSTRAINT ensembl_pseudogene_exons_pkey PRIMARY KEY (id),
  CONSTRAINT un_ensembl_pseduogene_exons__region_start_stop UNIQUE (region_id, exon_start, exon_stop),
  CONSTRAINT ensembl_pseduogene_exons_region_id_fkey FOREIGN KEY (region_id) REFERENCES ensembl_pseudogene_regions(id) ON DELETE CASCADE
);

-- relation-detector-fixture-table: case_01.ensembl_pseudogene_regions
CREATE TABLE case_01.ensembl_pseudogene_regions (
  id bigint DEFAULT nextval('ensembl_pseudogene_regions_id_seq'::regclass) NOT NULL,
  gene text NOT NULL,
  region_name text NOT NULL,
  chromosome text NOT NULL,
  strand integer NOT NULL,
  region_start integer NOT NULL,
  region_stop integer NOT NULL,
  assembly_id character varying(255),
  exon_count integer NOT NULL,
  CONSTRAINT ensembl_pseudogene_regions_pkey PRIMARY KEY (id),
  CONSTRAINT ensembl_pseduogene_regions_assembly_id_fkey FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX un__ensembl_pseudogene_regions_region_name ON case_01.ensembl_pseudogene_regions USING btree (md5(region_name));

-- relation-detector-fixture-table: case_01.ensembl_stable_prefixes
CREATE TABLE case_01.ensembl_stable_prefixes (
  taxid bigint NOT NULL,
  stable_id text,
  CONSTRAINT ensembl_stable_prefixes_pkey PRIMARY KEY (taxid)
);

-- relation-detector-fixture-table: case_01.go_term_annotations
CREATE TABLE case_01.go_term_annotations (
  go_term_annotation_id integer DEFAULT nextval('go_term_annotations_go_term_annotation_id_seq'::regclass) NOT NULL,
  rna_id character varying(50) NOT NULL,
  qualifier text NOT NULL,
  ontology_term_id character varying(10) NOT NULL,
  evidence_code character varying(11) NOT NULL,
  assigned_by character varying(50),
  extensions jsonb,
  CONSTRAINT go_term_annotations_pkey PRIMARY KEY (go_term_annotation_id),
  CONSTRAINT un_go_term_annotations UNIQUE (rna_id, qualifier, ontology_term_id, evidence_code, assigned_by),
  CONSTRAINT go_term_annotations_evidence_code_fkey FOREIGN KEY (evidence_code) REFERENCES ontology_terms(ontology_term_id),
  CONSTRAINT go_term_annotations_ontology_term_id_fkey FOREIGN KEY (ontology_term_id) REFERENCES ontology_terms(ontology_term_id)
);

-- relation-detector-fixture-table: case_01.go_term_publication_map
CREATE TABLE case_01.go_term_publication_map (
  go_term_publication_mapping_id integer DEFAULT nextval('go_term_publication_map_go_term_publication_mapping_id_seq1'::regclass) NOT NULL,
  go_term_annotation_id integer NOT NULL,
  reference_id bigint NOT NULL,
  CONSTRAINT go_term_publication_map_pkey1 PRIMARY KEY (go_term_publication_mapping_id),
  CONSTRAINT un_go_term_publication_map_anno_ref UNIQUE (go_term_annotation_id, reference_id),
  CONSTRAINT go_term_publication_map_go_term_annotation_id_fkey1 FOREIGN KEY (go_term_annotation_id) REFERENCES go_term_annotations(go_term_annotation_id),
  CONSTRAINT go_term_publication_map_reference_id_fkey FOREIGN KEY (reference_id) REFERENCES rnc_references(id)
);

-- relation-detector-fixture-table: case_01.insdc_so_term_mapping
CREATE TABLE case_01.insdc_so_term_mapping (
  rna_type text NOT NULL,
  so_term_id text NOT NULL,
  CONSTRAINT insdc_so_term_mapping_pkey PRIMARY KEY (rna_type),
  CONSTRAINT insdc_so_term_mapping_so_term_id_fkey FOREIGN KEY (so_term_id) REFERENCES ontology_terms(ontology_term_id)
);

-- relation-detector-fixture-table: case_01.litscan_ensembl_ids
CREATE TABLE case_01.litscan_ensembl_ids (
  id character varying
);
CREATE INDEX idx_litscan_ensembl_ids_id ON case_01.litscan_ensembl_ids USING btree (lower((id)::text));

-- relation-detector-fixture-table: case_01.litscan_sentence_id_counts
CREATE TABLE case_01.litscan_sentence_id_counts (
  sent_ids integer,
  id_count bigint
);

-- relation-detector-fixture-table: case_01.litscan_statistics
CREATE TABLE case_01.litscan_statistics (
  id integer DEFAULT nextval('litscan_statistics_id_seq'::regclass) NOT NULL,
  searched_ids integer,
  articles integer,
  ids_in_use integer,
  urs integer,
  expert_db integer,
  CONSTRAINT litscan_statistics_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.litsumm_summaries
CREATE TABLE case_01.litsumm_summaries (
  id integer DEFAULT nextval('litsumm_summaries_id_seq'::regclass) NOT NULL,
  rna_id text,
  context text,
  summary text,
  cost double precision,
  total_tokens integer,
  attempts integer,
  truthful boolean,
  problem_summary boolean,
  consistency_check_result text,
  selection_method text,
  rescue_prompts text[],
  primary_id character varying(44),
  display_id character varying(100),
  should_show boolean,
  CONSTRAINT litsumm_summaries_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.old_summaries
CREATE TABLE case_01.old_summaries (
  id integer,
  rna_id text,
  context text,
  summary text,
  cost double precision,
  total_tokens integer,
  attempts integer,
  truthful boolean,
  problem_summary boolean,
  consistency_check_result text,
  selection_method text
);

-- relation-detector-fixture-table: case_01.ontology_terms
CREATE TABLE case_01.ontology_terms (
  ontology_term_id character varying(15) NOT NULL,
  ontology character varying(5),
  name text,
  definition text,
  CONSTRAINT ontology_terms_pkey PRIMARY KEY (ontology_term_id)
);

-- relation-detector-fixture-table: case_01.pipeline_tracking_genome_mapping
CREATE TABLE case_01.pipeline_tracking_genome_mapping (
  id bigint DEFAULT nextval('pipeline_tracking_genome_mapping_id_seq'::regclass) NOT NULL,
  urs_taxid text NOT NULL,
  assembly_id text NOT NULL,
  last_run timestamp without time zone NOT NULL,
  CONSTRAINT pipeline_tracking_genome_mapping_pkey PRIMARY KEY (id),
  CONSTRAINT pipeline_tracking_genome_mapping_urs_taxid_assembly_key UNIQUE (urs_taxid, assembly_id),
  CONSTRAINT pipeline_tracking_genome_mapping_urs_taxid_fkey FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id)
);

-- relation-detector-fixture-table: case_01.pipeline_tracking_qa_scan
CREATE TABLE case_01.pipeline_tracking_qa_scan (
  id bigint DEFAULT nextval('pipeline_tracking_scan_id_seq'::regclass) NOT NULL,
  urs text NOT NULL,
  model_source text NOT NULL,
  source_version text NOT NULL,
  last_run timestamp without time zone NOT NULL,
  CONSTRAINT pipeline_tracking_scan_pkey PRIMARY KEY (id),
  CONSTRAINT pipeline_tracking_scan_urs_fkey FOREIGN KEY (urs) REFERENCES rna(upi)
);
CREATE UNIQUE INDEX un_pipeline_tracking_scan_urs_model ON case_01.pipeline_tracking_qa_scan USING btree (urs, model_source);

-- relation-detector-fixture-table: case_01.pipeline_tracking_traveler
CREATE TABLE case_01.pipeline_tracking_traveler (
  id bigint DEFAULT nextval('pipeline_tracking_traveler_id_seq'::regclass) NOT NULL,
  urs text NOT NULL,
  last_run timestamp without time zone NOT NULL,
  r2dt_version text,
  CONSTRAINT pipeline_tracking_traveler_pkey PRIMARY KEY (id),
  CONSTRAINT pipeline_tracking_traveler_urs_key UNIQUE (urs),
  CONSTRAINT pipeline_tracking_traveler_urs_fkey FOREIGN KEY (urs) REFERENCES rna(upi)
);

-- relation-detector-fixture-table: case_01.precompute_urs
CREATE TABLE case_01.precompute_urs (
  id bigint DEFAULT nextval('precompute_urs_id_seq'::regclass) NOT NULL,
  urs text NOT NULL,
  CONSTRAINT precompute_urs_pkey PRIMARY KEY (id),
  CONSTRAINT un_precompute_urs__urs UNIQUE (urs),
  CONSTRAINT fk_precompute_urs__urs FOREIGN KEY (urs) REFERENCES rna(upi)
);

-- relation-detector-fixture-table: case_01.precompute_urs_accession
CREATE TABLE case_01.precompute_urs_accession (
  id bigint DEFAULT nextval('precompute_urs_accession_id_seq'::regclass) NOT NULL,
  precompute_urs_id bigint NOT NULL,
  precompute_urs_taxid_id bigint NOT NULL,
  urs_taxid text NOT NULL,
  urs text NOT NULL,
  taxid integer NOT NULL,
  is_active boolean NOT NULL,
  last_release integer NOT NULL,
  accession text NOT NULL,
  database text NOT NULL,
  description text NOT NULL,
  gene text,
  optional_id text,
  species text,
  common_name text,
  feature_name text,
  ncrna_class text,
  locus_tag text,
  organelle text,
  lineage text,
  so_rna_type text,
  CONSTRAINT precompute_urs_accession_pkey PRIMARY KEY (id)
);
CREATE INDEX ix_precompute_urs_accession__precompute_id ON case_01.precompute_urs_accession USING btree (precompute_urs_id);
CREATE INDEX ix_precompute_urs_accession__urs_taxid ON case_01.precompute_urs_accession USING btree (urs_taxid);

-- relation-detector-fixture-table: case_01.precompute_urs_taxid
CREATE TABLE case_01.precompute_urs_taxid (
  id bigint DEFAULT nextval('precompute_urs_taxid_id_seq'::regclass) NOT NULL,
  precompute_urs_id bigint NOT NULL,
  urs text NOT NULL,
  taxid integer NOT NULL,
  urs_taxid text NOT NULL,
  CONSTRAINT precompute_urs_taxid_pkey PRIMARY KEY (id),
  CONSTRAINT un_precompute_urs_taxid__urs__taxid UNIQUE (urs, taxid),
  CONSTRAINT un_precompute_urs_taxid__urs_taxid UNIQUE (urs_taxid),
  CONSTRAINT fk_precompute_urs_taxid__urs FOREIGN KEY (urs) REFERENCES rna(upi),
  CONSTRAINT fk_precompute_urs_taxid__urs_id FOREIGN KEY (precompute_urs_id) REFERENCES precompute_urs(id)
);

-- relation-detector-fixture-table: case_01.protein_info
CREATE TABLE case_01.protein_info (
  protein_accession text NOT NULL,
  description text,
  label text,
  synonyms text[],
  CONSTRAINT protein_info_pkey PRIMARY KEY (protein_accession)
);

-- relation-detector-fixture-table: case_01.publications
CREATE TABLE case_01.publications (
  id integer DEFAULT nextval('publications_id_seq'::regclass) NOT NULL,
  database character varying(40) NOT NULL,
  total_ids integer NOT NULL,
  results integer NOT NULL,
  CONSTRAINT publications_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.qa_status
CREATE TABLE case_01.qa_status (
  rna_id character varying(50) NOT NULL,
  upi character varying(30) NOT NULL,
  taxid integer NOT NULL,
  has_issue boolean NOT NULL,
  incomplete_sequence boolean NOT NULL,
  possible_contamination boolean NOT NULL,
  missing_rfam_match boolean NOT NULL,
  messages jsonb NOT NULL,
  from_repetitive_region boolean NOT NULL,
  possible_orf boolean NOT NULL,
  CONSTRAINT qa_status_pkey PRIMARY KEY (rna_id),
  CONSTRAINT qa_status_upi_fkey FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX qa_status__has_issue ON case_01.qa_status USING btree (has_issue) WHERE has_issue;
CREATE INDEX qa_status__upi_taxid ON case_01.qa_status USING btree (upi, taxid);

-- relation-detector-fixture-table: case_01.r2dt_model_extra_data
CREATE TABLE case_01.r2dt_model_extra_data (
  id integer DEFAULT nextval('r2dt_model_extra_data_id_seq'::regclass) NOT NULL,
  model_id integer,
  model_name text,
  data jsonb,
  CONSTRAINT r2dt_model_extra_data_pkey PRIMARY KEY (id),
  CONSTRAINT r2dt_model_extra_data_model_id_fkey FOREIGN KEY (model_id) REFERENCES r2dt_models(id)
);

-- relation-detector-fixture-table: case_01.r2dt_models
CREATE TABLE case_01.r2dt_models (
  id integer DEFAULT nextval('rnc_secondary_structure_layout_models_id_seq'::regclass) NOT NULL,
  model_name text NOT NULL,
  rna_type text NOT NULL,
  so_term_id text NOT NULL,
  model_source text NOT NULL,
  model_length integer NOT NULL,
  model_basepair_count integer NOT NULL,
  CONSTRAINT rnc_secondary_structure_layout_models_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_secondary_structure_layout_models_model_name_key UNIQUE (model_name),
  CONSTRAINT rnc_secondary_structure_layout_models_so_term_id_fkey FOREIGN KEY (so_term_id) REFERENCES ontology_terms(ontology_term_id)
);
CREATE INDEX fk_rnc_secondary_structure_layout_models__model_name ON case_01.r2dt_models USING btree (model_name);
CREATE INDEX rnc_secondary_structure_layout_models_id_model_source_idx ON case_01.r2dt_models USING btree (id, model_source);
CREATE INDEX rnc_secondary_structure_layout_models_model_source_idx ON case_01.r2dt_models USING btree (model_source);

-- relation-detector-fixture-table: case_01.r2dt_models_backup
CREATE TABLE case_01.r2dt_models_backup (
  id integer,
  model_name text,
  taxid integer,
  cellular_location text,
  rna_type text,
  so_term_id text,
  model_source text,
  model_length integer,
  model_basepair_count integer
);

-- relation-detector-fixture-table: case_01.r2dt_results
CREATE TABLE case_01.r2dt_results (
  urs text NOT NULL,
  secondary_structure text NOT NULL,
  overlap_count integer NOT NULL,
  basepair_count integer NOT NULL,
  model_start integer,
  model_stop integer,
  sequence_start integer,
  sequence_stop integer,
  sequence_coverage double precision,
  model_id integer NOT NULL,
  id bigint DEFAULT nextval('load_secondary_layout_id_seq'::regclass) NOT NULL,
  inferred_should_show boolean DEFAULT true NOT NULL,
  model_coverage double precision,
  assigned_should_show boolean,
  CONSTRAINT load_secondary_layout_pkey PRIMARY KEY (id),
  CONSTRAINT un_layout__urs UNIQUE (urs),
  CONSTRAINT fk_layout__model_id FOREIGN KEY (model_id) REFERENCES r2dt_models(id),
  CONSTRAINT fk_layout__urs FOREIGN KEY (urs) REFERENCES rna(upi)
);
CREATE INDEX ix_layout__model_id ON case_01.r2dt_results USING btree (model_id);
CREATE INDEX ix_layout__urs ON case_01.r2dt_results USING btree (urs);

-- relation-detector-fixture-table: case_01.release_stats
CREATE TABLE case_01.release_stats (
  dbid bigint,
  this_release bigint NOT NULL,
  prev_release bigint,
  start_time timestamp without time zone,
  end_time timestamp without time zone,
  ff_loaded_rows bigint,
  retired_prev_releases bigint,
  retired_this_release bigint,
  retired_next_releases bigint,
  retired_total bigint,
  created_w_predecessors_v_1 bigint,
  created_w_predecessors_v_gt1 bigint,
  created_w_predecessors bigint,
  created_wo_predecessors_v_1 bigint,
  created_wo_predecessors_v_gt1 bigint,
  created_wo_predecessors bigint,
  active_created_prev_releases bigint,
  active_created_this_release bigint,
  active_created_next_releases bigint,
  created_this_release bigint,
  active_updated_this_release bigint,
  active_untouched_this_release bigint,
  active_total bigint,
  ff_taxid_nulls bigint,
  CONSTRAINT release_stats_pkey PRIMARY KEY (this_release)
);

-- relation-detector-fixture-table: case_01.rfam_analyzed_sequences
CREATE TABLE case_01.rfam_analyzed_sequences (
  upi character varying(13) NOT NULL,
  date date NOT NULL,
  total_matches integer DEFAULT 0 NOT NULL,
  rfam_version character varying(5),
  total_family_matches integer DEFAULT 0 NOT NULL,
  CONSTRAINT rfam_analyzed_sequences_pkey PRIMARY KEY (upi),
  CONSTRAINT rfam_analyzed_sequences_upi_143bc1f118b7f227_fk_rna_upi FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX rfam_analyzed_sequences_upi_143bc1f118b7f227_like ON case_01.rfam_analyzed_sequences USING btree (upi varchar_pattern_ops);

-- relation-detector-fixture-table: case_01.rfam_clans
CREATE TABLE case_01.rfam_clans (
  rfam_clan_id character varying(20) NOT NULL,
  name text NOT NULL,
  description text NOT NULL,
  family_count integer NOT NULL,
  CONSTRAINT rfam_clans_pkey PRIMARY KEY (rfam_clan_id)
);
CREATE INDEX rfam_clans_rfam_clan_id_bb628fbf349ed40_like ON case_01.rfam_clans USING btree (rfam_clan_id varchar_pattern_ops);

-- relation-detector-fixture-table: case_01.rfam_go_terms
CREATE TABLE case_01.rfam_go_terms (
  rfam_go_term_id integer DEFAULT nextval('rfam_go_terms_rfam_go_term_id_seq'::regclass) NOT NULL,
  rfam_model_id character varying(20) NOT NULL,
  ontology_term_id character varying(15) NOT NULL,
  CONSTRAINT rfam_go_terms_pkey PRIMARY KEY (rfam_go_term_id),
  CONSTRAINT un_rfam_go_terms__rfam_model_id_ontology_term_id UNIQUE (rfam_model_id, ontology_term_id),
  CONSTRAINT rfa_rfam_model_id_418d94a91e74ecaa_fk_rfam_models_rfam_model_id FOREIGN KEY (rfam_model_id) REFERENCES rfam_models(rfam_model_id) DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT rfam_go_terms_ontology_term_id_fkey FOREIGN KEY (ontology_term_id) REFERENCES ontology_terms(ontology_term_id)
);
CREATE INDEX rfam_go_terms_4582906f ON case_01.rfam_go_terms USING btree (rfam_model_id);
CREATE INDEX rfam_go_terms_rfam_model_id_418d94a91e74ecaa_like ON case_01.rfam_go_terms USING btree (rfam_model_id varchar_pattern_ops);

-- relation-detector-fixture-table: case_01.rfam_model_hits
CREATE TABLE case_01.rfam_model_hits (
  rfam_hit_id integer DEFAULT nextval('rfam_model_hits_rfam_hit_id_seq'::regclass) NOT NULL,
  sequence_start integer NOT NULL,
  sequence_stop integer NOT NULL,
  sequence_completeness double precision NOT NULL,
  model_start integer NOT NULL,
  model_stop integer NOT NULL,
  model_completeness double precision NOT NULL,
  overlap character varying(30) NOT NULL,
  e_value double precision NOT NULL,
  score double precision NOT NULL,
  rfam_model_id character varying(20) NOT NULL,
  upi character varying(13) NOT NULL,
  rnc_sequence_features_id integer,
  CONSTRAINT rfam_model_hits_pkey PRIMARY KEY (rfam_hit_id),
  CONSTRAINT un_rfam_model_hits_unique_cols UNIQUE (sequence_start, sequence_stop, model_start, model_stop, rfam_model_id, upi),
  CONSTRAINT rfa_rfam_model_id_5088d5c42ad2571a_fk_rfam_models_rfam_model_id FOREIGN KEY (rfam_model_id) REFERENCES rfam_models(rfam_model_id) DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT rfam_model_hits_rnc_sequence_features_id_fkey FOREIGN KEY (rnc_sequence_features_id) REFERENCES rnc_sequence_features(rnc_sequence_features_id),
  CONSTRAINT rfam_model_hits_upi_4c2c11c85f9de4b0_fk_rna_upi FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX rfam_model_hits_4582906f ON case_01.rfam_model_hits USING btree (rfam_model_id);
CREATE INDEX rfam_model_hits_98db0b07 ON case_01.rfam_model_hits USING btree (upi);

-- relation-detector-fixture-table: case_01.rfam_models
CREATE TABLE case_01.rfam_models (
  rfam_model_id character varying(20) NOT NULL,
  short_name character varying(50) NOT NULL,
  long_name character varying(200) NOT NULL,
  description character varying(2000),
  seed_count integer NOT NULL,
  full_count integer NOT NULL,
  length integer NOT NULL,
  is_suppressed boolean NOT NULL,
  domain character varying(50),
  rna_type character varying(250) NOT NULL,
  rfam_clan_id character varying(20),
  rfam_rna_type text NOT NULL,
  so_rna_type text,
  CONSTRAINT rfam_models_pkey PRIMARY KEY (rfam_model_id),
  CONSTRAINT rfam_m_rfam_clan_id_4497ffd203fbfac6_fk_rfam_clans_rfam_clan_id FOREIGN KEY (rfam_clan_id) REFERENCES rfam_clans(rfam_clan_id) DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT rfam_models_so_rna_type_fkey FOREIGN KEY (so_rna_type) REFERENCES ontology_terms(ontology_term_id)
);
CREATE INDEX rfam_models_21e3d921 ON case_01.rfam_models USING btree (rfam_clan_id);
CREATE INDEX rfam_models_rfam_clan_id_4497ffd203fbfac6_like ON case_01.rfam_models USING btree (rfam_clan_id varchar_pattern_ops);
CREATE INDEX rfam_models_rfam_model_id_3cafff1ce352ae9c_like ON case_01.rfam_models USING btree (rfam_model_id varchar_pattern_ops);

-- relation-detector-fixture-table: case_01.rna
CREATE TABLE case_01.rna (
  id bigint,
  upi character varying(30) NOT NULL,
  timestamp timestamp without time zone,
  userstamp character varying(60),
  crc64 character(16),
  len integer,
  seq_short character varying(4000),
  seq_long text,
  md5 character varying(64),
  CONSTRAINT rna_pkey PRIMARY KEY (upi)
);
CREATE INDEX idx_rna_upi ON case_01.rna USING btree (upi);
CREATE UNIQUE INDEX "rna$id" ON case_01.rna USING btree (id);
CREATE INDEX "rna$len" ON case_01.rna USING btree (len);
CREATE UNIQUE INDEX "rna$md5" ON case_01.rna USING btree (md5);

-- relation-detector-fixture-table: case_01.rnc_accession_active
CREATE TABLE case_01.rnc_accession_active (
  accession character varying(300) NOT NULL,
  urs character varying(26) NOT NULL,
  taxid integer NOT NULL,
  urs_taxid text NOT NULL,
  CONSTRAINT rnc_accession_active_pkey PRIMARY KEY (accession)
);

-- relation-detector-fixture-table: case_01.rnc_accession_sequence_feature
CREATE TABLE case_01.rnc_accession_sequence_feature (
  id bigint DEFAULT nextval('rnc_accessions_sequence_features_id_seq'::regclass) NOT NULL,
  rnc_sequence_feature_id integer NOT NULL,
  accession text NOT NULL,
  CONSTRAINT rnc_accessions_sequence_features_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_accessions_sequence_featu_accession_rnc_sequence_featur_key UNIQUE (accession, rnc_sequence_feature_id),
  CONSTRAINT rnc_accessions_sequence_features_accession_fkey FOREIGN KEY (accession) REFERENCES rnc_accessions(accession),
  CONSTRAINT rnc_accessions_sequence_features_rnc_sequence_feature_id_fkey FOREIGN KEY (rnc_sequence_feature_id) REFERENCES rnc_sequence_features(rnc_sequence_features_id)
);

-- relation-detector-fixture-table: case_01.rnc_accession_sequence_region
CREATE TABLE case_01.rnc_accession_sequence_region (
  id bigint DEFAULT nextval('rnc_accession_sequence_region_id_seq'::regclass) NOT NULL,
  accession text NOT NULL,
  region_id bigint NOT NULL,
  CONSTRAINT rnc_accession_sequence_region_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_accession_sequence_region_accession_region_id_key UNIQUE (accession, region_id),
  CONSTRAINT rnc_accession_sequence_region_accession_fkey FOREIGN KEY (accession) REFERENCES rnc_accessions(accession),
  CONSTRAINT rnc_accession_sequence_region_region_id_fkey FOREIGN KEY (region_id) REFERENCES rnc_sequence_regions(id)
);
CREATE INDEX idx_rnc_accession_sequence_region_region_id ON case_01.rnc_accession_sequence_region USING btree (region_id);

-- relation-detector-fixture-table: case_01.rnc_accessions
CREATE TABLE case_01.rnc_accessions (
  id bigint DEFAULT nextval('rnc_accessions_seq'::regclass),
  accession character varying(200) NOT NULL,
  parent_ac character varying(200),
  seq_version bigint,
  feature_start bigint,
  feature_end bigint,
  feature_name character varying(80),
  ordinal bigint,
  division character varying(6),
  keywords character varying(200),
  description character varying(500),
  species character varying(300),
  organelle character varying(200),
  classification character varying(1000),
  project character varying(100),
  is_composite character varying(2),
  non_coding_id character varying(200),
  database character varying(40),
  external_id character varying(300),
  optional_id character varying(200),
  common_name character varying(200),
  allele character varying(100),
  anticodon character varying(200),
  chromosome character varying(200),
  experiment text,
  function character varying(4000),
  gene character varying(200),
  gene_synonym character varying(800),
  inference character varying(600),
  locus_tag character varying(100),
  map character varying(400),
  mol_type character varying(100),
  ncrna_class character varying(100),
  note text,
  old_locus_tag character varying(200),
  operon character varying(100),
  product character varying(600),
  pseudogene character varying(100),
  standard_name character varying(200),
  db_xref text,
  rna_type character varying(15),
  url text,
  CONSTRAINT rnc_accessions_pkey PRIMARY KEY (accession),
  CONSTRAINT rnc_accessions_rna_type_fkey FOREIGN KEY (rna_type) REFERENCES ontology_terms(ontology_term_id)
);
CREATE INDEX "rnc_accessions$database" ON case_01.rnc_accessions USING btree (database);
CREATE INDEX "rnc_accessions$db_xref" ON case_01.rnc_accessions USING gin (db_xref gin_trgm_ops);
CREATE INDEX "rnc_accessions$external_id" ON case_01.rnc_accessions USING btree (external_id);
CREATE INDEX "rnc_accessions$feature_name" ON case_01.rnc_accessions USING btree (feature_name);
CREATE INDEX "rnc_accessions$is_composite" ON case_01.rnc_accessions USING btree (is_composite);
CREATE INDEX "rnc_accessions$locus_tag" ON case_01.rnc_accessions USING btree (locus_tag);
CREATE INDEX "rnc_accessions$optional_id" ON case_01.rnc_accessions USING btree (optional_id);
CREATE INDEX "rnc_accessions$parent_ac" ON case_01.rnc_accessions USING btree (parent_ac);
CREATE INDEX "rnc_accessions$species" ON case_01.rnc_accessions USING btree (species);
CREATE INDEX rnc_accessions_ncrna_class_index ON case_01.rnc_accessions USING btree (ncrna_class);

-- relation-detector-fixture-table: case_01.rnc_chemical_components
CREATE TABLE case_01.rnc_chemical_components (
  id character varying(16) NOT NULL,
  description character varying(1000),
  one_letter_code character varying(2),
  ccd_id character varying(6) DEFAULT 'NULL'::character varying,
  source character varying(20) DEFAULT 'NULL'::character varying,
  modomics_short_name character varying(40) DEFAULT 'NULL'::character varying,
  CONSTRAINT rnc_chemical_components_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.rnc_database
CREATE TABLE case_01.rnc_database (
  id bigint NOT NULL,
  timestamp timestamp without time zone NOT NULL,
  userstamp character varying(30) NOT NULL,
  descr character varying(60) NOT NULL,
  current_release integer,
  full_descr character varying(1024),
  alive character varying(1) NOT NULL,
  for_release character(1),
  display_name character varying(60),
  project_id character varying(20),
  avg_length bigint,
  min_length bigint,
  max_length bigint,
  num_sequences bigint,
  num_organisms bigint,
  description character varying(1024),
  url character varying(500),
  example jsonb,
  reference jsonb,
  CONSTRAINT rnc_database_pkey PRIMARY KEY (id)
);
CREATE UNIQUE INDEX "rnc_database$descr" ON case_01.rnc_database USING btree (descr);

-- relation-detector-fixture-table: case_01.rnc_database_json_stats
CREATE TABLE case_01.rnc_database_json_stats (
  database character varying(40) NOT NULL,
  length_counts text,
  taxonomic_lineage text,
  CONSTRAINT rnc_database_json_stats_pkey PRIMARY KEY (database)
);

-- relation-detector-fixture-table: case_01.rnc_database_references
CREATE TABLE case_01.rnc_database_references (
  id bigint DEFAULT nextval('rnc_database_references_id_seq'::regclass) NOT NULL,
  dbid integer NOT NULL,
  reference_id bigint NOT NULL,
  CONSTRAINT rnc_database_references_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_database_references_dbid_reference_id_key UNIQUE (dbid, reference_id),
  CONSTRAINT rnc_database_references_dbid_fkey FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT rnc_database_references_reference_id_fkey FOREIGN KEY (reference_id) REFERENCES rnc_references(id)
);

-- relation-detector-fixture-table: case_01.rnc_feedback_overlap
CREATE TABLE case_01.rnc_feedback_overlap (
  upi_taxid text NOT NULL,
  overlaps_with text[] NOT NULL,
  no_overlaps_with text[] NOT NULL,
  overlapping_upis text[],
  assembly_id character varying(255) NOT NULL,
  should_ignore boolean DEFAULT false,
  id bigint DEFAULT nextval('rnc_feedback_overlap_id_seq'::regclass) NOT NULL,
  CONSTRAINT rnc_feedback_overlap_pkey PRIMARY KEY (id),
  CONSTRAINT un_rnc_feedback_overlap__upi_assembly UNIQUE (upi_taxid, assembly_id),
  CONSTRAINT rnc_feedback_overlap_assembly_id_fkey FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE,
  CONSTRAINT rnc_feedback_overlap_upi_taxid_fkey FOREIGN KEY (upi_taxid) REFERENCES rnc_rna_precomputed(id)
);
CREATE INDEX idx_rnc__feedback_overlap__no_overlaps_with ON case_01.rnc_feedback_overlap USING gin (no_overlaps_with);
CREATE INDEX idx_rnc__feedback_overlap__overlaps_with ON case_01.rnc_feedback_overlap USING gin (overlaps_with);

-- relation-detector-fixture-table: case_01.rnc_feedback_target_assemblies
CREATE TABLE case_01.rnc_feedback_target_assemblies (
  assembly_id character varying(255),
  chromosome text,
  dbid bigint,
  database character varying(60),
  CONSTRAINT fk_rnc_feedback_target_assemblies__assembly_id FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE,
  CONSTRAINT fk_rnc_feedback_target_assemblies__dbid FOREIGN KEY (dbid) REFERENCES rnc_database(id) ON DELETE CASCADE
);

-- relation-detector-fixture-table: case_01.rnc_gene_status
CREATE TABLE case_01.rnc_gene_status (
  id bigint DEFAULT nextval('rnc_gene_status_id_seq'::regclass) NOT NULL,
  assembly_id text NOT NULL,
  urs_taxid text NOT NULL,
  region_id integer NOT NULL,
  status text NOT NULL,
  CONSTRAINT rnc_gene_status_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_gene_status_region_id_key UNIQUE (region_id),
  CONSTRAINT rnc_gene_status_assembly_id_fkey FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE,
  CONSTRAINT rnc_gene_status_region_id_fkey FOREIGN KEY (region_id) REFERENCES rnc_sequence_regions(id) ON DELETE CASCADE,
  CONSTRAINT rnc_gene_status_urs_taxid_fkey FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id) ON DELETE CASCADE
);
CREATE INDEX ix_rnc_gene_status__urs_taxid ON case_01.rnc_gene_status USING btree (urs_taxid);

-- relation-detector-fixture-table: case_01.rnc_import_tracker
CREATE TABLE case_01.rnc_import_tracker (
  id bigint DEFAULT nextval('rnc_import_tracker_id_seq'::regclass) NOT NULL,
  db_name character varying(60) NOT NULL,
  db_id bigint DEFAULT nextval('rnc_import_tracker_db_id_seq'::regclass) NOT NULL,
  last_import_date timestamp without time zone,
  file_md5 character varying(64),
  CONSTRAINT rnc_import_tracker_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_import_tracker_db_id_fkey FOREIGN KEY (db_id) REFERENCES rnc_database(id)
);

-- relation-detector-fixture-table: case_01.rnc_interactions
CREATE TABLE case_01.rnc_interactions (
  id bigint DEFAULT nextval('rnc_interactions_id_seq'::regclass) NOT NULL,
  intact_id text NOT NULL,
  urs_taxid text NOT NULL,
  interacting_id text NOT NULL,
  names jsonb NOT NULL,
  taxid integer NOT NULL,
  CONSTRAINT rnc_interactions_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_interactions_intact_id_key UNIQUE (intact_id),
  CONSTRAINT rnc_interactions_urs_taxid_fkey FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id)
);

-- relation-detector-fixture-table: case_01.rnc_locus
CREATE TABLE case_01.rnc_locus (
  id bigint DEFAULT nextval('rnc_locus_id_seq'::regclass) NOT NULL,
  assembly_id text NOT NULL,
  locus_name text NOT NULL,
  public_locus_name text NOT NULL,
  chromosome text NOT NULL,
  strand text NOT NULL,
  locus_start integer NOT NULL,
  locus_stop integer NOT NULL,
  member_count integer NOT NULL,
  CONSTRAINT rnc_locus_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_locus_assembly_id_locus_name_key UNIQUE (assembly_id, locus_name),
  CONSTRAINT rnc_locus_public_locus_name_key UNIQUE (public_locus_name),
  CONSTRAINT rnc_locus_assembly_id_fkey FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE
);

-- relation-detector-fixture-table: case_01.rnc_locus_members
CREATE TABLE case_01.rnc_locus_members (
  id bigint DEFAULT nextval('rnc_locus_members_id_seq'::regclass) NOT NULL,
  urs_taxid text NOT NULL,
  region_id integer NOT NULL,
  locus_id bigint NOT NULL,
  membership_status text NOT NULL,
  CONSTRAINT rnc_locus_members_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_locus_members_region_id_key UNIQUE (region_id),
  CONSTRAINT rnc_locus_members_locus_id_fkey FOREIGN KEY (locus_id) REFERENCES rnc_locus(id) ON DELETE CASCADE,
  CONSTRAINT rnc_locus_members_region_id_fkey FOREIGN KEY (region_id) REFERENCES rnc_sequence_regions(id) ON DELETE CASCADE,
  CONSTRAINT rnc_locus_members_urs_taxid_fkey FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id) ON DELETE CASCADE
);

-- relation-detector-fixture-table: case_01.rnc_modifications
CREATE TABLE case_01.rnc_modifications (
  id bigint NOT NULL,
  position bigint NOT NULL,
  author_assigned_position bigint NOT NULL,
  modification_id character varying(16) NOT NULL,
  upi character varying(26) NOT NULL,
  accession character varying(200) NOT NULL,
  CONSTRAINT rnc_modifications_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_modifications_rnc_accessions__fk FOREIGN KEY (accession) REFERENCES rnc_accessions(accession)
);
CREATE INDEX rnc_modifications_accession_index ON case_01.rnc_modifications USING btree (accession);
CREATE INDEX rnc_modifications_modification_id_idx ON case_01.rnc_modifications USING btree (modification_id);
CREATE INDEX rnc_modifications_upi_idx ON case_01.rnc_modifications USING btree (upi);

-- relation-detector-fixture-table: case_01.rnc_reference_map
CREATE TABLE case_01.rnc_reference_map (
  id bigint DEFAULT nextval('rnc_reference_map_seq'::regclass) NOT NULL,
  accession character varying(200) NOT NULL,
  reference_id bigint NOT NULL,
  CONSTRAINT rnc_reference_map_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_references_map$accession$reference_id UNIQUE (accession, reference_id),
  CONSTRAINT ck_rnc_reference_map__reference_id FOREIGN KEY (reference_id) REFERENCES rnc_references(id)
);
CREATE INDEX ix_rnc_references__reference_id_accesion ON case_01.rnc_reference_map USING btree (reference_id, accession);
CREATE INDEX "rnc_reference_map$reference_id" ON case_01.rnc_reference_map USING btree (reference_id);
CREATE INDEX "rnc_references_map$accession" ON case_01.rnc_reference_map USING btree (accession);
CREATE INDEX "rnc_references_map$reference_id" ON case_01.rnc_reference_map USING btree (reference_id);

-- relation-detector-fixture-table: case_01.rnc_references
CREATE TABLE case_01.rnc_references (
  id bigint DEFAULT nextval('rnc_refs_pk_seq'::regclass) NOT NULL,
  md5 character varying(64) NOT NULL,
  authors text,
  location character varying(4000),
  title character varying(4000),
  pmid character varying(40),
  pmcid character varying(40),
  epmcid character varying(40),
  doi character varying(160),
  epmc_updated smallint,
  CONSTRAINT rnc_references_pkey PRIMARY KEY (id)
);
CREATE INDEX "rnc_references$pmid" ON case_01.rnc_references USING btree (pmid);

-- relation-detector-fixture-table: case_01.rnc_related_sequences
CREATE TABLE case_01.rnc_related_sequences (
  id integer DEFAULT nextval('rnc_related_sequences_id_seq'::regclass) NOT NULL,
  source_urs_taxid character varying(50) NOT NULL,
  source_accession character varying(100) NOT NULL,
  target_urs_taxid character varying(50),
  target_accession character varying(100) NOT NULL,
  methods text[],
  relationship_type text DEFAULT ''::text NOT NULL,
  CONSTRAINT rnc_related_sequences_pkey PRIMARY KEY (id),
  CONSTRAINT fk_rnc_related_sequences__relationship_type FOREIGN KEY (relationship_type) REFERENCES rnc_relationship_types(relationship_type),
  CONSTRAINT rnc_related_sequences_source_accession_fkey FOREIGN KEY (source_accession) REFERENCES rnc_accessions(accession),
  CONSTRAINT rnc_related_sequences_source_urs_taxid_fkey FOREIGN KEY (source_urs_taxid) REFERENCES rnc_rna_precomputed(id),
  CONSTRAINT rnc_related_sequences_target_urs_taxid_fkey FOREIGN KEY (target_urs_taxid) REFERENCES rnc_rna_precomputed(id)
);
CREATE INDEX ix_rnc_related_sequences__target_ac ON case_01.rnc_related_sequences USING btree (target_accession);
CREATE INDEX rnc_related_relationship_type_idx ON case_01.rnc_related_sequences USING btree (relationship_type);
CREATE INDEX rnc_related_sequences_source_urs_taxid_idx ON case_01.rnc_related_sequences USING btree (source_urs_taxid);
CREATE INDEX rnc_related_sequences_target_urs_taxid_idx ON case_01.rnc_related_sequences USING btree (target_urs_taxid);
CREATE UNIQUE INDEX un_rnc_related_sequences__source_ac_target_ac_relationship_tyoe ON case_01.rnc_related_sequences USING btree (source_accession, target_accession, relationship_type);

-- relation-detector-fixture-table: case_01.rnc_relationship_types
CREATE TABLE case_01.rnc_relationship_types (
  relationship_type text NOT NULL,
  CONSTRAINT rnc_relationship_types_pkey PRIMARY KEY (relationship_type)
);

-- relation-detector-fixture-table: case_01.rnc_release
CREATE TABLE case_01.rnc_release (
  id bigint NOT NULL,
  dbid bigint NOT NULL,
  release_date timestamp without time zone NOT NULL,
  release_type character(1) NOT NULL,
  status character(1) NOT NULL,
  timestamp timestamp without time zone NOT NULL,
  userstamp character varying(30) NOT NULL,
  descr character varying(32),
  force_load character(1),
  CONSTRAINT rnc_release_pkey PRIMARY KEY (id)
);

-- relation-detector-fixture-table: case_01.rnc_rna_precomputed
CREATE TABLE case_01.rnc_rna_precomputed (
  id character varying(44) NOT NULL,
  taxid bigint,
  description character varying(500),
  upi character varying(26) NOT NULL,
  rna_type character varying(500) DEFAULT 'NULL'::character varying,
  update_date date DEFAULT ('now'::text)::date,
  has_coordinates boolean DEFAULT false,
  databases text,
  is_active boolean,
  last_release integer DEFAULT 1 NOT NULL,
  short_description text,
  so_rna_type text,
  is_locus_representative boolean DEFAULT true,
  assigned_so_rna_type text,
  CONSTRAINT rnc_rna_precomputed_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_rna_precomputed_assigned_so_rna_type_fkey FOREIGN KEY (assigned_so_rna_type) REFERENCES ontology_terms(ontology_term_id),
  CONSTRAINT rnc_rna_precomputed_last_release_fkey FOREIGN KEY (last_release) REFERENCES rnc_release(id),
  CONSTRAINT rnc_rna_precomputed_so_rna_type_fkey FOREIGN KEY (so_rna_type) REFERENCES ontology_terms(ontology_term_id)
);
CREATE INDEX ix_rnc_rna_precomputed__upi_taxid_last_release ON case_01.rnc_rna_precomputed USING btree (upi, taxid, last_release);
CREATE INDEX ix_rnc_rna_precomputed_assigned_rna ON case_01.rnc_rna_precomputed USING btree (assigned_so_rna_type);
CREATE INDEX rnc_rna_precomputed_98db0b07 ON case_01.rnc_rna_precomputed USING btree (upi);
CREATE INDEX rnc_rna_precomputed_is_active_idx ON case_01.rnc_rna_precomputed USING btree (is_active);
CREATE INDEX rnc_rna_precomputed_rna_type_idx ON case_01.rnc_rna_precomputed USING btree (rna_type);
CREATE INDEX rnc_rna_precomputed_upi_idx ON case_01.rnc_rna_precomputed USING btree (upi, taxid);

-- relation-detector-fixture-table: case_01.rnc_secondary_structure
CREATE TABLE case_01.rnc_secondary_structure (
  id integer,
  secondary_structure text,
  md5 character varying(32),
  rnc_accession_id character varying(100)
);

-- relation-detector-fixture-table: case_01.rnc_sequence_exons
CREATE TABLE case_01.rnc_sequence_exons (
  id integer DEFAULT nextval('rnc_sequence_exons_id_seq'::regclass) NOT NULL,
  region_id integer,
  exon_start integer NOT NULL,
  exon_stop integer NOT NULL,
  CONSTRAINT rnc_sequence_exons_pkey PRIMARY KEY (id),
  CONSTRAINT un_rnc_sequence_exons__region_start_stop UNIQUE (region_id, exon_start, exon_stop),
  CONSTRAINT rnc_sequence_exons_region_id_fkey FOREIGN KEY (region_id) REFERENCES rnc_sequence_regions(id) ON DELETE CASCADE
);

-- relation-detector-fixture-table: case_01.rnc_sequence_feature_providers
CREATE TABLE case_01.rnc_sequence_feature_providers (
  id bigint DEFAULT nextval('rnc_sequence_feature_providers_id_seq'::regclass) NOT NULL,
  name text,
  type text,
  description text,
  CONSTRAINT name_unique UNIQUE (name)
);

-- relation-detector-fixture-table: case_01.rnc_sequence_feature_types
CREATE TABLE case_01.rnc_sequence_feature_types (
  feature_name text NOT NULL,
  pretty_name text NOT NULL,
  CONSTRAINT rnc_sequence_feature_types_pkey PRIMARY KEY (feature_name)
);

-- relation-detector-fixture-table: case_01.rnc_sequence_features
CREATE TABLE case_01.rnc_sequence_features (
  rnc_sequence_features_id integer DEFAULT nextval('rnc_sequence_features_rnc_sequence_features_id_seq'::regclass) NOT NULL,
  upi character varying(100) NOT NULL,
  taxid integer,
  accession character varying(100),
  start integer NOT NULL,
  stop integer NOT NULL,
  feature_name character varying(50) NOT NULL,
  metadata jsonb,
  feature_provider text,
  CONSTRAINT rnc_sequence_features_pkey PRIMARY KEY (rnc_sequence_features_id),
  CONSTRAINT feature_provider_fk FOREIGN KEY (feature_provider) REFERENCES rnc_sequence_feature_providers(name),
  CONSTRAINT fk_rnc_sequence_features__feature_type FOREIGN KEY (feature_name) REFERENCES rnc_sequence_feature_types(feature_name),
  CONSTRAINT fk_rnc_sequence_features__taxid FOREIGN KEY (taxid) REFERENCES rnc_taxonomy(id),
  CONSTRAINT rnc_sequence_features_accession_fkey FOREIGN KEY (accession) REFERENCES rnc_accessions(accession),
  CONSTRAINT rnc_sequence_features_upi_fkey FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX ix_rnc_sequence_features__upi_taxid_name ON case_01.rnc_sequence_features USING btree (upi, taxid, feature_name);
CREATE INDEX ix_rnx_sequence_features_upi ON case_01.rnc_sequence_features USING btree (upi);
CREATE UNIQUE INDEX un_rnc_sequence_features__upi_taxid_accession_start_stop_featur ON case_01.rnc_sequence_features USING btree (upi, taxid, accession, start, stop, feature_name);

-- relation-detector-fixture-table: case_01.rnc_sequence_regions
CREATE TABLE case_01.rnc_sequence_regions (
  id integer DEFAULT nextval('rnc_sequence_regions_id_seq'::regclass) NOT NULL,
  urs_taxid text NOT NULL,
  region_name text NOT NULL,
  chromosome text NOT NULL,
  strand integer NOT NULL,
  region_start integer NOT NULL,
  region_stop integer NOT NULL,
  assembly_id character varying(255),
  was_mapped boolean NOT NULL,
  identity double precision,
  providing_databases text[],
  exon_count integer NOT NULL,
  CONSTRAINT rnc_sequence_regions_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_sequence_regions_assembly_id_fkey FOREIGN KEY (assembly_id) REFERENCES ensembl_assembly(assembly_id) ON DELETE CASCADE,
  CONSTRAINT rnc_sequence_regions_rnc_rna_precomputed__fk FOREIGN KEY (urs_taxid) REFERENCES rnc_rna_precomputed(id) ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX idx_rnc_sequence_regions_id ON case_01.rnc_sequence_regions USING btree (id);
CREATE INDEX idx_rnc_sequence_regions_not_mapped ON case_01.rnc_sequence_regions USING btree (id) WHERE (was_mapped = false);
CREATE INDEX ix_rnc_sequence_regions__assembly_id ON case_01.rnc_sequence_regions USING btree (assembly_id);
CREATE INDEX ix_sequence_regions__assembly_id_chromosome ON case_01.rnc_sequence_regions USING btree (assembly_id, chromosome);
CREATE INDEX ix_sequence_regions__assembly_id_chromosome_start_stop ON case_01.rnc_sequence_regions USING btree (assembly_id, chromosome, region_start, region_stop);
CREATE INDEX ix_sequence_regions__assembly_urs_taxid ON case_01.rnc_sequence_regions USING btree (urs_taxid, assembly_id);
CREATE INDEX ix_sequence_regions__urs_taxid ON case_01.rnc_sequence_regions USING btree (urs_taxid);
CREATE UNIQUE INDEX un_rnc_sequence_regions__region_name_assembly_id ON case_01.rnc_sequence_regions USING btree (md5(region_name), assembly_id);

-- relation-detector-fixture-table: case_01.rnc_taxonomy
CREATE TABLE case_01.rnc_taxonomy (
  id integer NOT NULL,
  name text NOT NULL,
  lineage text NOT NULL,
  aliases text[],
  replaced_by integer,
  common_name text,
  is_deleted boolean DEFAULT false NOT NULL,
  CONSTRAINT rnc_taxonomy_pkey PRIMARY KEY (id),
  CONSTRAINT rnc_taxonomy_replaced_by_fkey FOREIGN KEY (replaced_by) REFERENCES rnc_taxonomy(id)
);

-- relation-detector-fixture-table: case_01.sequence_region_urs_counts
CREATE TABLE case_01.sequence_region_urs_counts (
  sequence_region_active_urs_counts_id bigint DEFAULT nextval('sequence_region_urs_counts_sequence_region_active_urs_count_seq'::regclass) NOT NULL,
  urs_taxid text NOT NULL,
  assembly_id text NOT NULL,
  is_active boolean NOT NULL,
  mapped integer NOT NULL,
  provided integer NOT NULL,
  CONSTRAINT sequence_region_urs_counts_pkey PRIMARY KEY (sequence_region_active_urs_counts_id),
  CONSTRAINT sequence_region_urs_counts_assembly_id_urs_taxid_key UNIQUE (assembly_id, urs_taxid)
);

-- relation-detector-fixture-table: case_01.validate_layout_counts
CREATE TABLE case_01.validate_layout_counts (
  name text NOT NULL,
  changed integer NOT NULL,
  unchanged integer NOT NULL,
  inserted integer NOT NULL,
  moved integer NOT NULL,
  rotated integer NOT NULL,
  total integer NOT NULL,
  rna_length integer,
  not_drawn integer,
  overlap_count integer,
  CONSTRAINT validate_layout_counts_name_fkey FOREIGN KEY (name) REFERENCES rna(upi)
);

-- relation-detector-fixture-table: case_01.validate_layout_hits
CREATE TABLE case_01.validate_layout_hits (
  urs text,
  sequence_rna_type character varying(500),
  sequence_taxid bigint,
  model_name text,
  model_rna_type text,
  model_source text,
  model_so_rna_type text,
  model_taxid integer
);

-- relation-detector-fixture-table: case_01.xref
CREATE TABLE case_01.xref (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass)
);
CREATE INDEX idx_xref_deleted_urs_lookup ON case_01.xref USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_upi_taxid_last ON case_01.xref USING btree (upi, taxid, last);
CREATE INDEX ix_xref__upi_taxid ON case_01.xref USING btree (upi, taxid);
CREATE INDEX "xref$ac" ON case_01.xref USING btree (ac);
CREATE INDEX "xref$created" ON case_01.xref USING btree (created);
CREATE INDEX "xref$dbid" ON case_01.xref USING btree (dbid);
CREATE UNIQUE INDEX "xref$id" ON case_01.xref USING btree (id);
CREATE INDEX "xref$last" ON case_01.xref USING btree (last);
CREATE INDEX "xref$taxid" ON case_01.xref USING btree (taxid);
CREATE INDEX "xref$upi" ON case_01.xref USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_not_unique
CREATE TABLE case_01.xref_not_unique (
  dbid smallint NOT NULL,
  release_id integer NOT NULL,
  upi character(13) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(30) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(100) NOT NULL,
  version integer,
  taxid bigint
);

-- relation-detector-fixture-table: case_01.xref_p10_deleted
CREATE TABLE case_01.xref_p10_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p10_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p10_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p10_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p10_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p10_deleted_deleted_urs_lookup ON case_01.xref_p10_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p10_deleted_upi_taxid_last ON case_01.xref_p10_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p10_deleted__upi_taxid ON case_01.xref_p10_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p10_deleted$ac" ON case_01.xref_p10_deleted USING btree (ac);
CREATE INDEX "xref_p10_deleted$created" ON case_01.xref_p10_deleted USING btree (created);
CREATE INDEX "xref_p10_deleted$dbid" ON case_01.xref_p10_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p10_deleted$id" ON case_01.xref_p10_deleted USING btree (id);
CREATE INDEX "xref_p10_deleted$last" ON case_01.xref_p10_deleted USING btree (last);
CREATE INDEX "xref_p10_deleted$taxid" ON case_01.xref_p10_deleted USING btree (taxid);
CREATE INDEX "xref_p10_deleted$upi" ON case_01.xref_p10_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p10_not_deleted
CREATE TABLE case_01.xref_p10_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p10_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p10_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p10_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p10_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p10_not_deleted_deleted_urs_lookup ON case_01.xref_p10_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p10_not_deleted_upi_taxid_last ON case_01.xref_p10_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p10_not_deleted__upi_taxid ON case_01.xref_p10_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p10_not_deleted$ac" ON case_01.xref_p10_not_deleted USING btree (ac);
CREATE INDEX "xref_p10_not_deleted$created" ON case_01.xref_p10_not_deleted USING btree (created);
CREATE INDEX "xref_p10_not_deleted$dbid" ON case_01.xref_p10_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p10_not_deleted$id" ON case_01.xref_p10_not_deleted USING btree (id);
CREATE INDEX "xref_p10_not_deleted$last" ON case_01.xref_p10_not_deleted USING btree (last);
CREATE INDEX "xref_p10_not_deleted$taxid" ON case_01.xref_p10_not_deleted USING btree (taxid);
CREATE INDEX "xref_p10_not_deleted$upi" ON case_01.xref_p10_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p11_deleted
CREATE TABLE case_01.xref_p11_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p11_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p11_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p11_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p11_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p11_deleted_deleted_urs_lookup ON case_01.xref_p11_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p11_deleted_upi_taxid_last ON case_01.xref_p11_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p11_deleted$ac" ON case_01.xref_p11_deleted USING btree (ac);
CREATE INDEX "xref_p11_deleted$created" ON case_01.xref_p11_deleted USING btree (created);
CREATE INDEX "xref_p11_deleted$dbid" ON case_01.xref_p11_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p11_deleted$id" ON case_01.xref_p11_deleted USING btree (id);
CREATE INDEX "xref_p11_deleted$last" ON case_01.xref_p11_deleted USING btree (last);
CREATE INDEX "xref_p11_deleted$taxid" ON case_01.xref_p11_deleted USING btree (taxid);
CREATE INDEX "xref_p11_deleted$upi" ON case_01.xref_p11_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p11_not_deleted
CREATE TABLE case_01.xref_p11_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p11_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p11_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p11_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p11_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p11_not_deleted_deleted_urs_lookup ON case_01.xref_p11_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p11_not_deleted_upi_taxid_last ON case_01.xref_p11_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p11_not_deleted$ac" ON case_01.xref_p11_not_deleted USING btree (ac);
CREATE INDEX "xref_p11_not_deleted$created" ON case_01.xref_p11_not_deleted USING btree (created);
CREATE INDEX "xref_p11_not_deleted$dbid" ON case_01.xref_p11_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p11_not_deleted$id" ON case_01.xref_p11_not_deleted USING btree (id);
CREATE INDEX "xref_p11_not_deleted$last" ON case_01.xref_p11_not_deleted USING btree (last);
CREATE INDEX "xref_p11_not_deleted$taxid" ON case_01.xref_p11_not_deleted USING btree (taxid);
CREATE INDEX "xref_p11_not_deleted$upi" ON case_01.xref_p11_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p12_deleted
CREATE TABLE case_01.xref_p12_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p12_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p12_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p12_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p12_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p12_deleted_deleted_urs_lookup ON case_01.xref_p12_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p12_deleted_upi_taxid_last ON case_01.xref_p12_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p12_deleted$ac" ON case_01.xref_p12_deleted USING btree (ac);
CREATE INDEX "xref_p12_deleted$created" ON case_01.xref_p12_deleted USING btree (created);
CREATE INDEX "xref_p12_deleted$dbid" ON case_01.xref_p12_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p12_deleted$id" ON case_01.xref_p12_deleted USING btree (id);
CREATE INDEX "xref_p12_deleted$last" ON case_01.xref_p12_deleted USING btree (last);
CREATE INDEX "xref_p12_deleted$taxid" ON case_01.xref_p12_deleted USING btree (taxid);
CREATE INDEX "xref_p12_deleted$upi" ON case_01.xref_p12_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p12_not_deleted
CREATE TABLE case_01.xref_p12_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p12_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p12_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p12_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p12_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p12_not_deleted_deleted_urs_lookup ON case_01.xref_p12_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p12_not_deleted_upi_taxid_last ON case_01.xref_p12_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p12_not_deleted$ac" ON case_01.xref_p12_not_deleted USING btree (ac);
CREATE INDEX "xref_p12_not_deleted$created" ON case_01.xref_p12_not_deleted USING btree (created);
CREATE INDEX "xref_p12_not_deleted$dbid" ON case_01.xref_p12_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p12_not_deleted$id" ON case_01.xref_p12_not_deleted USING btree (id);
CREATE INDEX "xref_p12_not_deleted$last" ON case_01.xref_p12_not_deleted USING btree (last);
CREATE INDEX "xref_p12_not_deleted$taxid" ON case_01.xref_p12_not_deleted USING btree (taxid);
CREATE INDEX "xref_p12_not_deleted$upi" ON case_01.xref_p12_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p13_deleted
CREATE TABLE case_01.xref_p13_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p13_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p13_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p13_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p13_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p13_deleted_deleted_urs_lookup ON case_01.xref_p13_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p13_deleted_upi_taxid_last ON case_01.xref_p13_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p13_deleted__upi_taxid ON case_01.xref_p13_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p13_deleted$ac" ON case_01.xref_p13_deleted USING btree (ac);
CREATE INDEX "xref_p13_deleted$created" ON case_01.xref_p13_deleted USING btree (created);
CREATE INDEX "xref_p13_deleted$dbid" ON case_01.xref_p13_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p13_deleted$id" ON case_01.xref_p13_deleted USING btree (id);
CREATE INDEX "xref_p13_deleted$last" ON case_01.xref_p13_deleted USING btree (last);
CREATE INDEX "xref_p13_deleted$taxid" ON case_01.xref_p13_deleted USING btree (taxid);
CREATE INDEX "xref_p13_deleted$upi" ON case_01.xref_p13_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p13_not_deleted
CREATE TABLE case_01.xref_p13_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p13_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p13_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p13_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p13_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p13_not_deleted_deleted_urs_lookup ON case_01.xref_p13_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p13_not_deleted_upi_taxid_last ON case_01.xref_p13_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p13_not_deleted__upi_taxid ON case_01.xref_p13_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p13_not_deleted$ac" ON case_01.xref_p13_not_deleted USING btree (ac);
CREATE INDEX "xref_p13_not_deleted$created" ON case_01.xref_p13_not_deleted USING btree (created);
CREATE INDEX "xref_p13_not_deleted$dbid" ON case_01.xref_p13_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p13_not_deleted$id" ON case_01.xref_p13_not_deleted USING btree (id);
CREATE INDEX "xref_p13_not_deleted$last" ON case_01.xref_p13_not_deleted USING btree (last);
CREATE INDEX "xref_p13_not_deleted$taxid" ON case_01.xref_p13_not_deleted USING btree (taxid);
CREATE INDEX "xref_p13_not_deleted$upi" ON case_01.xref_p13_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p14_deleted
CREATE TABLE case_01.xref_p14_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p14_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p14_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p14_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p14_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p14_deleted_deleted_urs_lookup ON case_01.xref_p14_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p14_deleted_upi_taxid_last ON case_01.xref_p14_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p14_deleted$ac" ON case_01.xref_p14_deleted USING btree (ac);
CREATE INDEX "xref_p14_deleted$created" ON case_01.xref_p14_deleted USING btree (created);
CREATE INDEX "xref_p14_deleted$dbid" ON case_01.xref_p14_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p14_deleted$id" ON case_01.xref_p14_deleted USING btree (id);
CREATE INDEX "xref_p14_deleted$last" ON case_01.xref_p14_deleted USING btree (last);
CREATE INDEX "xref_p14_deleted$taxid" ON case_01.xref_p14_deleted USING btree (taxid);
CREATE INDEX "xref_p14_deleted$upi" ON case_01.xref_p14_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p14_not_deleted
CREATE TABLE case_01.xref_p14_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p14_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p14_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p14_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p14_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p14_not_deleted_deleted_urs_lookup ON case_01.xref_p14_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p14_not_deleted_upi_taxid_last ON case_01.xref_p14_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p14_not_deleted$ac" ON case_01.xref_p14_not_deleted USING btree (ac);
CREATE INDEX "xref_p14_not_deleted$created" ON case_01.xref_p14_not_deleted USING btree (created);
CREATE INDEX "xref_p14_not_deleted$dbid" ON case_01.xref_p14_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p14_not_deleted$id" ON case_01.xref_p14_not_deleted USING btree (id);
CREATE INDEX "xref_p14_not_deleted$last" ON case_01.xref_p14_not_deleted USING btree (last);
CREATE INDEX "xref_p14_not_deleted$taxid" ON case_01.xref_p14_not_deleted USING btree (taxid);
CREATE INDEX "xref_p14_not_deleted$upi" ON case_01.xref_p14_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p15_deleted
CREATE TABLE case_01.xref_p15_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p15_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p15_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p15_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p15_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p15_deleted_deleted_urs_lookup ON case_01.xref_p15_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p15_deleted_upi_taxid_last ON case_01.xref_p15_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p15_deleted$ac" ON case_01.xref_p15_deleted USING btree (ac);
CREATE INDEX "xref_p15_deleted$created" ON case_01.xref_p15_deleted USING btree (created);
CREATE INDEX "xref_p15_deleted$dbid" ON case_01.xref_p15_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p15_deleted$id" ON case_01.xref_p15_deleted USING btree (id);
CREATE INDEX "xref_p15_deleted$last" ON case_01.xref_p15_deleted USING btree (last);
CREATE INDEX "xref_p15_deleted$taxid" ON case_01.xref_p15_deleted USING btree (taxid);
CREATE INDEX "xref_p15_deleted$upi" ON case_01.xref_p15_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p15_not_deleted
CREATE TABLE case_01.xref_p15_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p15_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p15_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p15_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p15_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p15_not_deleted_deleted_urs_lookup ON case_01.xref_p15_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p15_not_deleted_upi_taxid_last ON case_01.xref_p15_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p15_not_deleted$ac" ON case_01.xref_p15_not_deleted USING btree (ac);
CREATE INDEX "xref_p15_not_deleted$created" ON case_01.xref_p15_not_deleted USING btree (created);
CREATE INDEX "xref_p15_not_deleted$dbid" ON case_01.xref_p15_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p15_not_deleted$id" ON case_01.xref_p15_not_deleted USING btree (id);
CREATE INDEX "xref_p15_not_deleted$last" ON case_01.xref_p15_not_deleted USING btree (last);
CREATE INDEX "xref_p15_not_deleted$taxid" ON case_01.xref_p15_not_deleted USING btree (taxid);
CREATE INDEX "xref_p15_not_deleted$upi" ON case_01.xref_p15_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p16_deleted
CREATE TABLE case_01.xref_p16_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p16_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p16_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p16_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p16_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p16_deleted_deleted_urs_lookup ON case_01.xref_p16_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p16_deleted_upi_taxid_last ON case_01.xref_p16_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p16_deleted$ac" ON case_01.xref_p16_deleted USING btree (ac);
CREATE INDEX "xref_p16_deleted$created" ON case_01.xref_p16_deleted USING btree (created);
CREATE INDEX "xref_p16_deleted$dbid" ON case_01.xref_p16_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p16_deleted$id" ON case_01.xref_p16_deleted USING btree (id);
CREATE INDEX "xref_p16_deleted$last" ON case_01.xref_p16_deleted USING btree (last);
CREATE INDEX "xref_p16_deleted$taxid" ON case_01.xref_p16_deleted USING btree (taxid);
CREATE INDEX "xref_p16_deleted$upi" ON case_01.xref_p16_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p16_not_deleted
CREATE TABLE case_01.xref_p16_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p16_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p16_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p16_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p16_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p16_not_deleted_deleted_urs_lookup ON case_01.xref_p16_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p16_not_deleted_upi_taxid_last ON case_01.xref_p16_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p16_not_deleted$ac" ON case_01.xref_p16_not_deleted USING btree (ac);
CREATE INDEX "xref_p16_not_deleted$created" ON case_01.xref_p16_not_deleted USING btree (created);
CREATE INDEX "xref_p16_not_deleted$dbid" ON case_01.xref_p16_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p16_not_deleted$id" ON case_01.xref_p16_not_deleted USING btree (id);
CREATE INDEX "xref_p16_not_deleted$last" ON case_01.xref_p16_not_deleted USING btree (last);
CREATE INDEX "xref_p16_not_deleted$taxid" ON case_01.xref_p16_not_deleted USING btree (taxid);
CREATE INDEX "xref_p16_not_deleted$upi" ON case_01.xref_p16_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p17_deleted
CREATE TABLE case_01.xref_p17_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p17_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p17_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p17_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p17_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p17_deleted_deleted_urs_lookup ON case_01.xref_p17_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p17_deleted_upi_taxid_last ON case_01.xref_p17_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p17_deleted$ac" ON case_01.xref_p17_deleted USING btree (ac);
CREATE INDEX "xref_p17_deleted$created" ON case_01.xref_p17_deleted USING btree (created);
CREATE INDEX "xref_p17_deleted$dbid" ON case_01.xref_p17_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p17_deleted$id" ON case_01.xref_p17_deleted USING btree (id);
CREATE INDEX "xref_p17_deleted$last" ON case_01.xref_p17_deleted USING btree (last);
CREATE INDEX "xref_p17_deleted$taxid" ON case_01.xref_p17_deleted USING btree (taxid);
CREATE INDEX "xref_p17_deleted$upi" ON case_01.xref_p17_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p17_not_deleted
CREATE TABLE case_01.xref_p17_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p17_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p17_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p17_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p17_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p17_not_deleted_deleted_urs_lookup ON case_01.xref_p17_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p17_not_deleted_upi_taxid_last ON case_01.xref_p17_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p17_not_deleted$ac" ON case_01.xref_p17_not_deleted USING btree (ac);
CREATE INDEX "xref_p17_not_deleted$created" ON case_01.xref_p17_not_deleted USING btree (created);
CREATE INDEX "xref_p17_not_deleted$dbid" ON case_01.xref_p17_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p17_not_deleted$id" ON case_01.xref_p17_not_deleted USING btree (id);
CREATE INDEX "xref_p17_not_deleted$last" ON case_01.xref_p17_not_deleted USING btree (last);
CREATE INDEX "xref_p17_not_deleted$taxid" ON case_01.xref_p17_not_deleted USING btree (taxid);
CREATE INDEX "xref_p17_not_deleted$upi" ON case_01.xref_p17_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p18_deleted
CREATE TABLE case_01.xref_p18_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p18_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p18_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p18_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p18_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p18_deleted_deleted_urs_lookup ON case_01.xref_p18_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p18_deleted_upi_taxid_last ON case_01.xref_p18_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p18_deleted$ac" ON case_01.xref_p18_deleted USING btree (ac);
CREATE INDEX "xref_p18_deleted$created" ON case_01.xref_p18_deleted USING btree (created);
CREATE INDEX "xref_p18_deleted$dbid" ON case_01.xref_p18_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p18_deleted$id" ON case_01.xref_p18_deleted USING btree (id);
CREATE INDEX "xref_p18_deleted$last" ON case_01.xref_p18_deleted USING btree (last);
CREATE INDEX "xref_p18_deleted$taxid" ON case_01.xref_p18_deleted USING btree (taxid);
CREATE INDEX "xref_p18_deleted$upi" ON case_01.xref_p18_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p18_not_deleted
CREATE TABLE case_01.xref_p18_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p18_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p18_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p18_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p18_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p18_not_deleted_deleted_urs_lookup ON case_01.xref_p18_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p18_not_deleted_upi_taxid_last ON case_01.xref_p18_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p18_not_deleted$ac" ON case_01.xref_p18_not_deleted USING btree (ac);
CREATE INDEX "xref_p18_not_deleted$created" ON case_01.xref_p18_not_deleted USING btree (created);
CREATE INDEX "xref_p18_not_deleted$dbid" ON case_01.xref_p18_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p18_not_deleted$id" ON case_01.xref_p18_not_deleted USING btree (id);
CREATE INDEX "xref_p18_not_deleted$last" ON case_01.xref_p18_not_deleted USING btree (last);
CREATE INDEX "xref_p18_not_deleted$taxid" ON case_01.xref_p18_not_deleted USING btree (taxid);
CREATE INDEX "xref_p18_not_deleted$upi" ON case_01.xref_p18_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p19_deleted
CREATE TABLE case_01.xref_p19_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p19_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p19_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p19_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p19_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p19_deleted_deleted_urs_lookup ON case_01.xref_p19_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p19_deleted_upi_taxid_last ON case_01.xref_p19_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p19_deleted__upi_taxid ON case_01.xref_p19_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p19_deleted$ac" ON case_01.xref_p19_deleted USING btree (ac);
CREATE INDEX "xref_p19_deleted$created" ON case_01.xref_p19_deleted USING btree (created);
CREATE INDEX "xref_p19_deleted$dbid" ON case_01.xref_p19_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p19_deleted$id" ON case_01.xref_p19_deleted USING btree (id);
CREATE INDEX "xref_p19_deleted$last" ON case_01.xref_p19_deleted USING btree (last);
CREATE INDEX "xref_p19_deleted$taxid" ON case_01.xref_p19_deleted USING btree (taxid);
CREATE INDEX "xref_p19_deleted$upi" ON case_01.xref_p19_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p19_not_deleted
CREATE TABLE case_01.xref_p19_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p19_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p19_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p19_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p19_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p19_not_deleted_deleted_urs_lookup ON case_01.xref_p19_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p19_not_deleted_upi_taxid_last ON case_01.xref_p19_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p19_not_deleted__upi_taxid ON case_01.xref_p19_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p19_not_deleted$ac" ON case_01.xref_p19_not_deleted USING btree (ac);
CREATE INDEX "xref_p19_not_deleted$created" ON case_01.xref_p19_not_deleted USING btree (created);
CREATE INDEX "xref_p19_not_deleted$dbid" ON case_01.xref_p19_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p19_not_deleted$id" ON case_01.xref_p19_not_deleted USING btree (id);
CREATE INDEX "xref_p19_not_deleted$last" ON case_01.xref_p19_not_deleted USING btree (last);
CREATE INDEX "xref_p19_not_deleted$taxid" ON case_01.xref_p19_not_deleted USING btree (taxid);
CREATE INDEX "xref_p19_not_deleted$upi" ON case_01.xref_p19_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p1_deleted
CREATE TABLE case_01.xref_p1_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p1_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p1_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p1_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p1_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p1_deleted_deleted_urs_lookup ON case_01.xref_p1_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX "xref_p1_deleted$ac" ON case_01.xref_p1_deleted USING btree (ac);
CREATE INDEX "xref_p1_deleted$created" ON case_01.xref_p1_deleted USING btree (created);
CREATE INDEX "xref_p1_deleted$dbid" ON case_01.xref_p1_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p1_deleted$id" ON case_01.xref_p1_deleted USING btree (id);
CREATE INDEX "xref_p1_deleted$last" ON case_01.xref_p1_deleted USING btree (last);
CREATE INDEX "xref_p1_deleted$taxid" ON case_01.xref_p1_deleted USING btree (taxid);
CREATE INDEX "xref_p1_deleted$upi" ON case_01.xref_p1_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p1_not_deleted
CREATE TABLE case_01.xref_p1_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p1_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p1_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p1_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p1_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p1_not_deleted_deleted_urs_lookup ON case_01.xref_p1_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p1_not_deleted_upi_taxid_last ON case_01.xref_p1_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p1_not_deleted$ac" ON case_01.xref_p1_not_deleted USING btree (ac);
CREATE INDEX "xref_p1_not_deleted$created" ON case_01.xref_p1_not_deleted USING btree (created);
CREATE INDEX "xref_p1_not_deleted$dbid" ON case_01.xref_p1_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p1_not_deleted$id" ON case_01.xref_p1_not_deleted USING btree (id);
CREATE INDEX "xref_p1_not_deleted$last" ON case_01.xref_p1_not_deleted USING btree (last);
CREATE INDEX "xref_p1_not_deleted$taxid" ON case_01.xref_p1_not_deleted USING btree (taxid);
CREATE INDEX "xref_p1_not_deleted$upi" ON case_01.xref_p1_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p20_deleted
CREATE TABLE case_01.xref_p20_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p20_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p20_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p20_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p20_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p20_deleted_deleted_urs_lookup ON case_01.xref_p20_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p20_deleted_upi_taxid_last ON case_01.xref_p20_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p20_deleted$ac" ON case_01.xref_p20_deleted USING btree (ac);
CREATE INDEX "xref_p20_deleted$created" ON case_01.xref_p20_deleted USING btree (created);
CREATE INDEX "xref_p20_deleted$dbid" ON case_01.xref_p20_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p20_deleted$id" ON case_01.xref_p20_deleted USING btree (id);
CREATE INDEX "xref_p20_deleted$last" ON case_01.xref_p20_deleted USING btree (last);
CREATE INDEX "xref_p20_deleted$taxid" ON case_01.xref_p20_deleted USING btree (taxid);
CREATE INDEX "xref_p20_deleted$upi" ON case_01.xref_p20_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p20_not_deleted
CREATE TABLE case_01.xref_p20_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p20_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p20_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p20_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p20_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p20_not_deleted_deleted_urs_lookup ON case_01.xref_p20_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p20_not_deleted_upi_taxid_last ON case_01.xref_p20_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p20_not_deleted$ac" ON case_01.xref_p20_not_deleted USING btree (ac);
CREATE INDEX "xref_p20_not_deleted$created" ON case_01.xref_p20_not_deleted USING btree (created);
CREATE INDEX "xref_p20_not_deleted$dbid" ON case_01.xref_p20_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p20_not_deleted$id" ON case_01.xref_p20_not_deleted USING btree (id);
CREATE INDEX "xref_p20_not_deleted$last" ON case_01.xref_p20_not_deleted USING btree (last);
CREATE INDEX "xref_p20_not_deleted$taxid" ON case_01.xref_p20_not_deleted USING btree (taxid);
CREATE INDEX "xref_p20_not_deleted$upi" ON case_01.xref_p20_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p21_deleted
CREATE TABLE case_01.xref_p21_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p21_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p21_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p21_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p21_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p21_deleted_deleted_urs_lookup ON case_01.xref_p21_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p21_deleted_upi_taxid_last ON case_01.xref_p21_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p21_deleted__upi_taxid ON case_01.xref_p21_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p21_deleted$ac" ON case_01.xref_p21_deleted USING btree (ac);
CREATE INDEX "xref_p21_deleted$created" ON case_01.xref_p21_deleted USING btree (created);
CREATE INDEX "xref_p21_deleted$dbid" ON case_01.xref_p21_deleted USING btree (dbid);
CREATE INDEX "xref_p21_deleted$last" ON case_01.xref_p21_deleted USING btree (last);
CREATE INDEX "xref_p21_deleted$taxid" ON case_01.xref_p21_deleted USING btree (taxid);
CREATE INDEX "xref_p21_deleted$upi" ON case_01.xref_p21_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p21_not_deleted
CREATE TABLE case_01.xref_p21_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p21_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p21_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p21_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p21_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p21_not_deleted_deleted_urs_lookup ON case_01.xref_p21_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p21_not_deleted_upi_taxid_last ON case_01.xref_p21_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p21_not_deleted__upi_taxid ON case_01.xref_p21_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p21_not_deleted$ac" ON case_01.xref_p21_not_deleted USING btree (ac);
CREATE INDEX "xref_p21_not_deleted$created" ON case_01.xref_p21_not_deleted USING btree (created);
CREATE INDEX "xref_p21_not_deleted$dbid" ON case_01.xref_p21_not_deleted USING btree (dbid);
CREATE INDEX "xref_p21_not_deleted$last" ON case_01.xref_p21_not_deleted USING btree (last);
CREATE INDEX "xref_p21_not_deleted$taxid" ON case_01.xref_p21_not_deleted USING btree (taxid);
CREATE INDEX "xref_p21_not_deleted$upi" ON case_01.xref_p21_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p22_deleted
CREATE TABLE case_01.xref_p22_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p22_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p22_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p22_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p22_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p22_deleted_deleted_urs_lookup ON case_01.xref_p22_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p22_deleted_upi_taxid_last ON case_01.xref_p22_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p22_deleted__upi_taxid ON case_01.xref_p22_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p22_deleted$ac" ON case_01.xref_p22_deleted USING btree (ac);
CREATE INDEX "xref_p22_deleted$created" ON case_01.xref_p22_deleted USING btree (created);
CREATE INDEX "xref_p22_deleted$dbid" ON case_01.xref_p22_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p22_deleted$id" ON case_01.xref_p22_deleted USING btree (id);
CREATE INDEX "xref_p22_deleted$last" ON case_01.xref_p22_deleted USING btree (last);
CREATE INDEX "xref_p22_deleted$taxid" ON case_01.xref_p22_deleted USING btree (taxid);
CREATE INDEX "xref_p22_deleted$upi" ON case_01.xref_p22_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p22_not_deleted
CREATE TABLE case_01.xref_p22_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p22_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p22_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p22_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p22_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p22_not_deleted_deleted_urs_lookup ON case_01.xref_p22_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p22_not_deleted_upi_taxid_last ON case_01.xref_p22_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p22_not_deleted__upi_taxid ON case_01.xref_p22_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p22_not_deleted$ac" ON case_01.xref_p22_not_deleted USING btree (ac);
CREATE INDEX "xref_p22_not_deleted$created" ON case_01.xref_p22_not_deleted USING btree (created);
CREATE INDEX "xref_p22_not_deleted$dbid" ON case_01.xref_p22_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p22_not_deleted$id" ON case_01.xref_p22_not_deleted USING btree (id);
CREATE INDEX "xref_p22_not_deleted$last" ON case_01.xref_p22_not_deleted USING btree (last);
CREATE INDEX "xref_p22_not_deleted$taxid" ON case_01.xref_p22_not_deleted USING btree (taxid);
CREATE INDEX "xref_p22_not_deleted$upi" ON case_01.xref_p22_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p23_deleted
CREATE TABLE case_01.xref_p23_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p23_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p23_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p23_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p23_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p23_deleted_deleted_urs_lookup ON case_01.xref_p23_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p23_deleted_upi_taxid_last ON case_01.xref_p23_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p23_deleted$ac" ON case_01.xref_p23_deleted USING btree (ac);
CREATE INDEX "xref_p23_deleted$created" ON case_01.xref_p23_deleted USING btree (created);
CREATE INDEX "xref_p23_deleted$dbid" ON case_01.xref_p23_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p23_deleted$id" ON case_01.xref_p23_deleted USING btree (id);
CREATE INDEX "xref_p23_deleted$last" ON case_01.xref_p23_deleted USING btree (last);
CREATE INDEX "xref_p23_deleted$taxid" ON case_01.xref_p23_deleted USING btree (taxid);
CREATE INDEX "xref_p23_deleted$upi" ON case_01.xref_p23_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p23_not_deleted
CREATE TABLE case_01.xref_p23_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p23_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p23_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p23_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p23_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p23_not_deleted_deleted_urs_lookup ON case_01.xref_p23_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p23_not_deleted_upi_taxid_last ON case_01.xref_p23_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p23_not_deleted$ac" ON case_01.xref_p23_not_deleted USING btree (ac);
CREATE INDEX "xref_p23_not_deleted$created" ON case_01.xref_p23_not_deleted USING btree (created);
CREATE INDEX "xref_p23_not_deleted$dbid" ON case_01.xref_p23_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p23_not_deleted$id" ON case_01.xref_p23_not_deleted USING btree (id);
CREATE INDEX "xref_p23_not_deleted$last" ON case_01.xref_p23_not_deleted USING btree (last);
CREATE INDEX "xref_p23_not_deleted$taxid" ON case_01.xref_p23_not_deleted USING btree (taxid);
CREATE INDEX "xref_p23_not_deleted$upi" ON case_01.xref_p23_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p24_deleted
CREATE TABLE case_01.xref_p24_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p24_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p24_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p24_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p24_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p24_deleted_deleted_urs_lookup ON case_01.xref_p24_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p24_deleted_upi_taxid_last ON case_01.xref_p24_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p24_deleted$ac" ON case_01.xref_p24_deleted USING btree (ac);
CREATE INDEX "xref_p24_deleted$created" ON case_01.xref_p24_deleted USING btree (created);
CREATE INDEX "xref_p24_deleted$dbid" ON case_01.xref_p24_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p24_deleted$id" ON case_01.xref_p24_deleted USING btree (id);
CREATE INDEX "xref_p24_deleted$last" ON case_01.xref_p24_deleted USING btree (last);
CREATE INDEX "xref_p24_deleted$taxid" ON case_01.xref_p24_deleted USING btree (taxid);
CREATE INDEX "xref_p24_deleted$upi" ON case_01.xref_p24_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p24_not_deleted
CREATE TABLE case_01.xref_p24_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p24_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p24_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p24_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p24_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p24_not_deleted_deleted_urs_lookup ON case_01.xref_p24_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p24_not_deleted_upi_taxid_last ON case_01.xref_p24_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p24_not_deleted$ac" ON case_01.xref_p24_not_deleted USING btree (ac);
CREATE INDEX "xref_p24_not_deleted$created" ON case_01.xref_p24_not_deleted USING btree (created);
CREATE INDEX "xref_p24_not_deleted$dbid" ON case_01.xref_p24_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p24_not_deleted$id" ON case_01.xref_p24_not_deleted USING btree (id);
CREATE INDEX "xref_p24_not_deleted$last" ON case_01.xref_p24_not_deleted USING btree (last);
CREATE INDEX "xref_p24_not_deleted$taxid" ON case_01.xref_p24_not_deleted USING btree (taxid);
CREATE INDEX "xref_p24_not_deleted$upi" ON case_01.xref_p24_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p25_deleted
CREATE TABLE case_01.xref_p25_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p25_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p25_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p25_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p25_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p25_deleted_deleted_urs_lookup ON case_01.xref_p25_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p25_deleted_upi_taxid_last ON case_01.xref_p25_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p25_deleted$ac" ON case_01.xref_p25_deleted USING btree (ac);
CREATE INDEX "xref_p25_deleted$created" ON case_01.xref_p25_deleted USING btree (created);
CREATE INDEX "xref_p25_deleted$dbid" ON case_01.xref_p25_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p25_deleted$id" ON case_01.xref_p25_deleted USING btree (id);
CREATE INDEX "xref_p25_deleted$last" ON case_01.xref_p25_deleted USING btree (last);
CREATE INDEX "xref_p25_deleted$taxid" ON case_01.xref_p25_deleted USING btree (taxid);
CREATE INDEX "xref_p25_deleted$upi" ON case_01.xref_p25_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p25_not_deleted
CREATE TABLE case_01.xref_p25_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p25_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p25_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p25_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p25_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p25_not_deleted_deleted_urs_lookup ON case_01.xref_p25_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p25_not_deleted_upi_taxid_last ON case_01.xref_p25_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p25_not_deleted$ac" ON case_01.xref_p25_not_deleted USING btree (ac);
CREATE INDEX "xref_p25_not_deleted$created" ON case_01.xref_p25_not_deleted USING btree (created);
CREATE INDEX "xref_p25_not_deleted$dbid" ON case_01.xref_p25_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p25_not_deleted$id" ON case_01.xref_p25_not_deleted USING btree (id);
CREATE INDEX "xref_p25_not_deleted$last" ON case_01.xref_p25_not_deleted USING btree (last);
CREATE INDEX "xref_p25_not_deleted$taxid" ON case_01.xref_p25_not_deleted USING btree (taxid);
CREATE INDEX "xref_p25_not_deleted$upi" ON case_01.xref_p25_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p26_deleted
CREATE TABLE case_01.xref_p26_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p26_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p26_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p26_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p26_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p26_deleted_deleted_urs_lookup ON case_01.xref_p26_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p26_deleted_upi_taxid_last ON case_01.xref_p26_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p26_deleted$ac" ON case_01.xref_p26_deleted USING btree (ac);
CREATE INDEX "xref_p26_deleted$created" ON case_01.xref_p26_deleted USING btree (created);
CREATE INDEX "xref_p26_deleted$dbid" ON case_01.xref_p26_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p26_deleted$id" ON case_01.xref_p26_deleted USING btree (id);
CREATE INDEX "xref_p26_deleted$last" ON case_01.xref_p26_deleted USING btree (last);
CREATE INDEX "xref_p26_deleted$taxid" ON case_01.xref_p26_deleted USING btree (taxid);
CREATE INDEX "xref_p26_deleted$upi" ON case_01.xref_p26_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p26_not_deleted
CREATE TABLE case_01.xref_p26_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p26_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p26_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p26_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p26_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p26_not_deleted_deleted_urs_lookup ON case_01.xref_p26_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p26_not_deleted_upi_taxid_last ON case_01.xref_p26_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p26_not_deleted$ac" ON case_01.xref_p26_not_deleted USING btree (ac);
CREATE INDEX "xref_p26_not_deleted$created" ON case_01.xref_p26_not_deleted USING btree (created);
CREATE INDEX "xref_p26_not_deleted$dbid" ON case_01.xref_p26_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p26_not_deleted$id" ON case_01.xref_p26_not_deleted USING btree (id);
CREATE INDEX "xref_p26_not_deleted$last" ON case_01.xref_p26_not_deleted USING btree (last);
CREATE INDEX "xref_p26_not_deleted$taxid" ON case_01.xref_p26_not_deleted USING btree (taxid);
CREATE INDEX "xref_p26_not_deleted$upi" ON case_01.xref_p26_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p27_deleted
CREATE TABLE case_01.xref_p27_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p27_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p27_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p27_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p27_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p27_deleted_deleted_urs_lookup ON case_01.xref_p27_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p27_deleted_upi_taxid_last ON case_01.xref_p27_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p27_deleted__upi_taxid ON case_01.xref_p27_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p27_deleted$ac" ON case_01.xref_p27_deleted USING btree (ac);
CREATE INDEX "xref_p27_deleted$created" ON case_01.xref_p27_deleted USING btree (created);
CREATE INDEX "xref_p27_deleted$dbid" ON case_01.xref_p27_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p27_deleted$id" ON case_01.xref_p27_deleted USING btree (id);
CREATE INDEX "xref_p27_deleted$last" ON case_01.xref_p27_deleted USING btree (last);
CREATE INDEX "xref_p27_deleted$taxid" ON case_01.xref_p27_deleted USING btree (taxid);
CREATE INDEX "xref_p27_deleted$upi" ON case_01.xref_p27_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p27_not_deleted
CREATE TABLE case_01.xref_p27_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p27_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p27_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p27_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p27_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p27_not_deleted_deleted_urs_lookup ON case_01.xref_p27_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p27_not_deleted_upi_taxid_last ON case_01.xref_p27_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p27_not_deleted__upi_taxid ON case_01.xref_p27_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p27_not_deleted$ac" ON case_01.xref_p27_not_deleted USING btree (ac);
CREATE INDEX "xref_p27_not_deleted$created" ON case_01.xref_p27_not_deleted USING btree (created);
CREATE INDEX "xref_p27_not_deleted$dbid" ON case_01.xref_p27_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p27_not_deleted$id" ON case_01.xref_p27_not_deleted USING btree (id);
CREATE INDEX "xref_p27_not_deleted$last" ON case_01.xref_p27_not_deleted USING btree (last);
CREATE INDEX "xref_p27_not_deleted$taxid" ON case_01.xref_p27_not_deleted USING btree (taxid);
CREATE INDEX "xref_p27_not_deleted$upi" ON case_01.xref_p27_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p28_deleted
CREATE TABLE case_01.xref_p28_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p28_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p28_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p28_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p28_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p28_deleted_deleted_urs_lookup ON case_01.xref_p28_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p28_deleted_upi_taxid_last ON case_01.xref_p28_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p28_deleted__upi_taxid ON case_01.xref_p28_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p28_deleted$ac" ON case_01.xref_p28_deleted USING btree (ac);
CREATE INDEX "xref_p28_deleted$created" ON case_01.xref_p28_deleted USING btree (created);
CREATE INDEX "xref_p28_deleted$dbid" ON case_01.xref_p28_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p28_deleted$id" ON case_01.xref_p28_deleted USING btree (id);
CREATE INDEX "xref_p28_deleted$last" ON case_01.xref_p28_deleted USING btree (last);
CREATE INDEX "xref_p28_deleted$taxid" ON case_01.xref_p28_deleted USING btree (taxid);
CREATE INDEX "xref_p28_deleted$upi" ON case_01.xref_p28_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p28_not_deleted
CREATE TABLE case_01.xref_p28_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p28_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p28_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p28_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p28_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p28_not_deleted_deleted_urs_lookup ON case_01.xref_p28_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p28_not_deleted_upi_taxid_last ON case_01.xref_p28_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p28_not_deleted__upi_taxid ON case_01.xref_p28_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p28_not_deleted$ac" ON case_01.xref_p28_not_deleted USING btree (ac);
CREATE INDEX "xref_p28_not_deleted$created" ON case_01.xref_p28_not_deleted USING btree (created);
CREATE INDEX "xref_p28_not_deleted$dbid" ON case_01.xref_p28_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p28_not_deleted$id" ON case_01.xref_p28_not_deleted USING btree (id);
CREATE INDEX "xref_p28_not_deleted$last" ON case_01.xref_p28_not_deleted USING btree (last);
CREATE INDEX "xref_p28_not_deleted$taxid" ON case_01.xref_p28_not_deleted USING btree (taxid);
CREATE INDEX "xref_p28_not_deleted$upi" ON case_01.xref_p28_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p29_deleted
CREATE TABLE case_01.xref_p29_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p29_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p29_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p29_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p29_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p29_deleted_deleted_urs_lookup ON case_01.xref_p29_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p29_deleted_upi_taxid_last ON case_01.xref_p29_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p29_deleted$ac" ON case_01.xref_p29_deleted USING btree (ac);
CREATE INDEX "xref_p29_deleted$created" ON case_01.xref_p29_deleted USING btree (created);
CREATE INDEX "xref_p29_deleted$dbid" ON case_01.xref_p29_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p29_deleted$id" ON case_01.xref_p29_deleted USING btree (id);
CREATE INDEX "xref_p29_deleted$last" ON case_01.xref_p29_deleted USING btree (last);
CREATE INDEX "xref_p29_deleted$taxid" ON case_01.xref_p29_deleted USING btree (taxid);
CREATE INDEX "xref_p29_deleted$upi" ON case_01.xref_p29_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p29_not_deleted
CREATE TABLE case_01.xref_p29_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p29_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p29_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p29_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p29_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p29_not_deleted_deleted_urs_lookup ON case_01.xref_p29_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p29_not_deleted_upi_taxid_last ON case_01.xref_p29_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p29_not_deleted$ac" ON case_01.xref_p29_not_deleted USING btree (ac);
CREATE INDEX "xref_p29_not_deleted$created" ON case_01.xref_p29_not_deleted USING btree (created);
CREATE INDEX "xref_p29_not_deleted$dbid" ON case_01.xref_p29_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p29_not_deleted$id" ON case_01.xref_p29_not_deleted USING btree (id);
CREATE INDEX "xref_p29_not_deleted$last" ON case_01.xref_p29_not_deleted USING btree (last);
CREATE INDEX "xref_p29_not_deleted$taxid" ON case_01.xref_p29_not_deleted USING btree (taxid);
CREATE INDEX "xref_p29_not_deleted$upi" ON case_01.xref_p29_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p2_deleted
CREATE TABLE case_01.xref_p2_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p2_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p2_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p2_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p2_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p2_deleted_deleted_urs_lookup ON case_01.xref_p2_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p2_deleted_upi_taxid_last ON case_01.xref_p2_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p2_deleted$ac" ON case_01.xref_p2_deleted USING btree (ac);
CREATE INDEX "xref_p2_deleted$created" ON case_01.xref_p2_deleted USING btree (created);
CREATE INDEX "xref_p2_deleted$dbid" ON case_01.xref_p2_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p2_deleted$id" ON case_01.xref_p2_deleted USING btree (id);
CREATE INDEX "xref_p2_deleted$last" ON case_01.xref_p2_deleted USING btree (last);
CREATE INDEX "xref_p2_deleted$taxid" ON case_01.xref_p2_deleted USING btree (taxid);
CREATE INDEX "xref_p2_deleted$upi" ON case_01.xref_p2_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p2_not_deleted
CREATE TABLE case_01.xref_p2_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p2_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p2_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p2_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p2_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p2_not_deleted_deleted_urs_lookup ON case_01.xref_p2_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p2_not_deleted_upi_taxid_last ON case_01.xref_p2_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p2_not_deleted$ac" ON case_01.xref_p2_not_deleted USING btree (ac);
CREATE INDEX "xref_p2_not_deleted$created" ON case_01.xref_p2_not_deleted USING btree (created);
CREATE INDEX "xref_p2_not_deleted$dbid" ON case_01.xref_p2_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p2_not_deleted$id" ON case_01.xref_p2_not_deleted USING btree (id);
CREATE INDEX "xref_p2_not_deleted$last" ON case_01.xref_p2_not_deleted USING btree (last);
CREATE INDEX "xref_p2_not_deleted$taxid" ON case_01.xref_p2_not_deleted USING btree (taxid);
CREATE INDEX "xref_p2_not_deleted$upi" ON case_01.xref_p2_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p30_deleted
CREATE TABLE case_01.xref_p30_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p30_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p30_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p30_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p30_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p30_deleted_deleted_urs_lookup ON case_01.xref_p30_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p30_deleted_upi_taxid_last ON case_01.xref_p30_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p30_deleted$ac" ON case_01.xref_p30_deleted USING btree (ac);
CREATE INDEX "xref_p30_deleted$created" ON case_01.xref_p30_deleted USING btree (created);
CREATE INDEX "xref_p30_deleted$dbid" ON case_01.xref_p30_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p30_deleted$id" ON case_01.xref_p30_deleted USING btree (id);
CREATE INDEX "xref_p30_deleted$last" ON case_01.xref_p30_deleted USING btree (last);
CREATE INDEX "xref_p30_deleted$taxid" ON case_01.xref_p30_deleted USING btree (taxid);
CREATE INDEX "xref_p30_deleted$upi" ON case_01.xref_p30_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p30_not_deleted
CREATE TABLE case_01.xref_p30_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p30_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p30_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p30_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p30_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p30_not_deleted_deleted_urs_lookup ON case_01.xref_p30_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p30_not_deleted_upi_taxid_last ON case_01.xref_p30_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p30_not_deleted$ac" ON case_01.xref_p30_not_deleted USING btree (ac);
CREATE INDEX "xref_p30_not_deleted$created" ON case_01.xref_p30_not_deleted USING btree (created);
CREATE INDEX "xref_p30_not_deleted$dbid" ON case_01.xref_p30_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p30_not_deleted$id" ON case_01.xref_p30_not_deleted USING btree (id);
CREATE INDEX "xref_p30_not_deleted$last" ON case_01.xref_p30_not_deleted USING btree (last);
CREATE INDEX "xref_p30_not_deleted$taxid" ON case_01.xref_p30_not_deleted USING btree (taxid);
CREATE INDEX "xref_p30_not_deleted$upi" ON case_01.xref_p30_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p31_deleted
CREATE TABLE case_01.xref_p31_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p31_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p31_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p31_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p31_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p31_deleted_deleted_urs_lookup ON case_01.xref_p31_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p31_deleted_upi_taxid_last ON case_01.xref_p31_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p31_deleted$ac" ON case_01.xref_p31_deleted USING btree (ac);
CREATE INDEX "xref_p31_deleted$created" ON case_01.xref_p31_deleted USING btree (created);
CREATE INDEX "xref_p31_deleted$dbid" ON case_01.xref_p31_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p31_deleted$id" ON case_01.xref_p31_deleted USING btree (id);
CREATE INDEX "xref_p31_deleted$last" ON case_01.xref_p31_deleted USING btree (last);
CREATE INDEX "xref_p31_deleted$taxid" ON case_01.xref_p31_deleted USING btree (taxid);
CREATE INDEX "xref_p31_deleted$upi" ON case_01.xref_p31_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p31_not_deleted
CREATE TABLE case_01.xref_p31_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p31_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p31_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p31_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p31_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p31_not_deleted_deleted_urs_lookup ON case_01.xref_p31_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p31_not_deleted_upi_taxid_last ON case_01.xref_p31_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p31_not_deleted$ac" ON case_01.xref_p31_not_deleted USING btree (ac);
CREATE INDEX "xref_p31_not_deleted$created" ON case_01.xref_p31_not_deleted USING btree (created);
CREATE INDEX "xref_p31_not_deleted$dbid" ON case_01.xref_p31_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p31_not_deleted$id" ON case_01.xref_p31_not_deleted USING btree (id);
CREATE INDEX "xref_p31_not_deleted$last" ON case_01.xref_p31_not_deleted USING btree (last);
CREATE INDEX "xref_p31_not_deleted$taxid" ON case_01.xref_p31_not_deleted USING btree (taxid);
CREATE INDEX "xref_p31_not_deleted$upi" ON case_01.xref_p31_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p32_deleted
CREATE TABLE case_01.xref_p32_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p32_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p32_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p32_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p32_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p32_deleted_deleted_urs_lookup ON case_01.xref_p32_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p32_deleted_upi_taxid_last ON case_01.xref_p32_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p32_deleted$ac" ON case_01.xref_p32_deleted USING btree (ac);
CREATE INDEX "xref_p32_deleted$created" ON case_01.xref_p32_deleted USING btree (created);
CREATE INDEX "xref_p32_deleted$dbid" ON case_01.xref_p32_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p32_deleted$id" ON case_01.xref_p32_deleted USING btree (id);
CREATE INDEX "xref_p32_deleted$last" ON case_01.xref_p32_deleted USING btree (last);
CREATE INDEX "xref_p32_deleted$taxid" ON case_01.xref_p32_deleted USING btree (taxid);
CREATE INDEX "xref_p32_deleted$upi" ON case_01.xref_p32_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p32_not_deleted
CREATE TABLE case_01.xref_p32_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p32_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p32_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p32_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p32_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p32_not_deleted_deleted_urs_lookup ON case_01.xref_p32_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p32_not_deleted_upi_taxid_last ON case_01.xref_p32_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p32_not_deleted$ac" ON case_01.xref_p32_not_deleted USING btree (ac);
CREATE INDEX "xref_p32_not_deleted$created" ON case_01.xref_p32_not_deleted USING btree (created);
CREATE INDEX "xref_p32_not_deleted$dbid" ON case_01.xref_p32_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p32_not_deleted$id" ON case_01.xref_p32_not_deleted USING btree (id);
CREATE INDEX "xref_p32_not_deleted$last" ON case_01.xref_p32_not_deleted USING btree (last);
CREATE INDEX "xref_p32_not_deleted$taxid" ON case_01.xref_p32_not_deleted USING btree (taxid);
CREATE INDEX "xref_p32_not_deleted$upi" ON case_01.xref_p32_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p33_deleted
CREATE TABLE case_01.xref_p33_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p33_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p33_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p33_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p33_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p33_deleted_deleted_urs_lookup ON case_01.xref_p33_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p33_deleted_upi_taxid_last ON case_01.xref_p33_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p33_deleted$ac" ON case_01.xref_p33_deleted USING btree (ac);
CREATE INDEX "xref_p33_deleted$created" ON case_01.xref_p33_deleted USING btree (created);
CREATE INDEX "xref_p33_deleted$dbid" ON case_01.xref_p33_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p33_deleted$id" ON case_01.xref_p33_deleted USING btree (id);
CREATE INDEX "xref_p33_deleted$last" ON case_01.xref_p33_deleted USING btree (last);
CREATE INDEX "xref_p33_deleted$taxid" ON case_01.xref_p33_deleted USING btree (taxid);
CREATE INDEX "xref_p33_deleted$upi" ON case_01.xref_p33_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p33_not_deleted
CREATE TABLE case_01.xref_p33_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p33_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p33_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p33_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p33_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p33_not_deleted_deleted_urs_lookup ON case_01.xref_p33_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p33_not_deleted_upi_taxid_last ON case_01.xref_p33_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p33_not_deleted$ac" ON case_01.xref_p33_not_deleted USING btree (ac);
CREATE INDEX "xref_p33_not_deleted$created" ON case_01.xref_p33_not_deleted USING btree (created);
CREATE INDEX "xref_p33_not_deleted$dbid" ON case_01.xref_p33_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p33_not_deleted$id" ON case_01.xref_p33_not_deleted USING btree (id);
CREATE INDEX "xref_p33_not_deleted$last" ON case_01.xref_p33_not_deleted USING btree (last);
CREATE INDEX "xref_p33_not_deleted$taxid" ON case_01.xref_p33_not_deleted USING btree (taxid);
CREATE INDEX "xref_p33_not_deleted$upi" ON case_01.xref_p33_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p34_deleted
CREATE TABLE case_01.xref_p34_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p34_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p34_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p34_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p34_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p34_deleted_deleted_urs_lookup ON case_01.xref_p34_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p34_deleted_upi_taxid_last ON case_01.xref_p34_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p34_deleted$ac" ON case_01.xref_p34_deleted USING btree (ac);
CREATE INDEX "xref_p34_deleted$created" ON case_01.xref_p34_deleted USING btree (created);
CREATE INDEX "xref_p34_deleted$dbid" ON case_01.xref_p34_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p34_deleted$id" ON case_01.xref_p34_deleted USING btree (id);
CREATE INDEX "xref_p34_deleted$last" ON case_01.xref_p34_deleted USING btree (last);
CREATE INDEX "xref_p34_deleted$taxid" ON case_01.xref_p34_deleted USING btree (taxid);
CREATE INDEX "xref_p34_deleted$upi" ON case_01.xref_p34_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p34_not_deleted
CREATE TABLE case_01.xref_p34_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p34_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p34_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p34_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p34_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p34_not_deleted_deleted_urs_lookup ON case_01.xref_p34_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p34_not_deleted_upi_taxid_last ON case_01.xref_p34_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p34_not_deleted$ac" ON case_01.xref_p34_not_deleted USING btree (ac);
CREATE INDEX "xref_p34_not_deleted$created" ON case_01.xref_p34_not_deleted USING btree (created);
CREATE INDEX "xref_p34_not_deleted$dbid" ON case_01.xref_p34_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p34_not_deleted$id" ON case_01.xref_p34_not_deleted USING btree (id);
CREATE INDEX "xref_p34_not_deleted$last" ON case_01.xref_p34_not_deleted USING btree (last);
CREATE INDEX "xref_p34_not_deleted$taxid" ON case_01.xref_p34_not_deleted USING btree (taxid);
CREATE INDEX "xref_p34_not_deleted$upi" ON case_01.xref_p34_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p35_deleted
CREATE TABLE case_01.xref_p35_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p35_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p35_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p35_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p35_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p35_deleted_deleted_urs_lookup ON case_01.xref_p35_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p35_deleted_upi_taxid_last ON case_01.xref_p35_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p35_deleted$ac" ON case_01.xref_p35_deleted USING btree (ac);
CREATE INDEX "xref_p35_deleted$created" ON case_01.xref_p35_deleted USING btree (created);
CREATE INDEX "xref_p35_deleted$dbid" ON case_01.xref_p35_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p35_deleted$id" ON case_01.xref_p35_deleted USING btree (id);
CREATE INDEX "xref_p35_deleted$last" ON case_01.xref_p35_deleted USING btree (last);
CREATE INDEX "xref_p35_deleted$taxid" ON case_01.xref_p35_deleted USING btree (taxid);
CREATE INDEX "xref_p35_deleted$upi" ON case_01.xref_p35_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p35_not_deleted
CREATE TABLE case_01.xref_p35_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p35_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p35_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p35_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p35_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p35_not_deleted_deleted_urs_lookup ON case_01.xref_p35_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p35_not_deleted_upi_taxid_last ON case_01.xref_p35_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p35_not_deleted$ac" ON case_01.xref_p35_not_deleted USING btree (ac);
CREATE INDEX "xref_p35_not_deleted$created" ON case_01.xref_p35_not_deleted USING btree (created);
CREATE INDEX "xref_p35_not_deleted$dbid" ON case_01.xref_p35_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p35_not_deleted$id" ON case_01.xref_p35_not_deleted USING btree (id);
CREATE INDEX "xref_p35_not_deleted$last" ON case_01.xref_p35_not_deleted USING btree (last);
CREATE INDEX "xref_p35_not_deleted$taxid" ON case_01.xref_p35_not_deleted USING btree (taxid);
CREATE INDEX "xref_p35_not_deleted$upi" ON case_01.xref_p35_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p36_deleted
CREATE TABLE case_01.xref_p36_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p36_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p36_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p36_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p36_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p36_deleted_deleted_urs_lookup ON case_01.xref_p36_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p36_deleted_upi_taxid_last ON case_01.xref_p36_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p36_deleted$ac" ON case_01.xref_p36_deleted USING btree (ac);
CREATE INDEX "xref_p36_deleted$created" ON case_01.xref_p36_deleted USING btree (created);
CREATE INDEX "xref_p36_deleted$dbid" ON case_01.xref_p36_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p36_deleted$id" ON case_01.xref_p36_deleted USING btree (id);
CREATE INDEX "xref_p36_deleted$last" ON case_01.xref_p36_deleted USING btree (last);
CREATE INDEX "xref_p36_deleted$taxid" ON case_01.xref_p36_deleted USING btree (taxid);
CREATE INDEX "xref_p36_deleted$upi" ON case_01.xref_p36_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p36_not_deleted
CREATE TABLE case_01.xref_p36_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p36_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p36_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p36_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p36_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p36_not_deleted_deleted_urs_lookup ON case_01.xref_p36_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p36_not_deleted_upi_taxid_last ON case_01.xref_p36_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p36_not_deleted$ac" ON case_01.xref_p36_not_deleted USING btree (ac);
CREATE INDEX "xref_p36_not_deleted$created" ON case_01.xref_p36_not_deleted USING btree (created);
CREATE INDEX "xref_p36_not_deleted$dbid" ON case_01.xref_p36_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p36_not_deleted$id" ON case_01.xref_p36_not_deleted USING btree (id);
CREATE INDEX "xref_p36_not_deleted$last" ON case_01.xref_p36_not_deleted USING btree (last);
CREATE INDEX "xref_p36_not_deleted$taxid" ON case_01.xref_p36_not_deleted USING btree (taxid);
CREATE INDEX "xref_p36_not_deleted$upi" ON case_01.xref_p36_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p37_deleted
CREATE TABLE case_01.xref_p37_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p37_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p37_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p37_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p37_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p37_deleted_deleted_urs_lookup ON case_01.xref_p37_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p37_deleted_upi_taxid_last ON case_01.xref_p37_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p37_deleted$ac" ON case_01.xref_p37_deleted USING btree (ac);
CREATE INDEX "xref_p37_deleted$created" ON case_01.xref_p37_deleted USING btree (created);
CREATE INDEX "xref_p37_deleted$dbid" ON case_01.xref_p37_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p37_deleted$id" ON case_01.xref_p37_deleted USING btree (id);
CREATE INDEX "xref_p37_deleted$last" ON case_01.xref_p37_deleted USING btree (last);
CREATE INDEX "xref_p37_deleted$taxid" ON case_01.xref_p37_deleted USING btree (taxid);
CREATE INDEX "xref_p37_deleted$upi" ON case_01.xref_p37_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p37_not_deleted
CREATE TABLE case_01.xref_p37_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p37_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p37_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p37_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p37_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p37_not_deleted_deleted_urs_lookup ON case_01.xref_p37_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p37_not_deleted_upi_taxid_last ON case_01.xref_p37_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p37_not_deleted$ac" ON case_01.xref_p37_not_deleted USING btree (ac);
CREATE INDEX "xref_p37_not_deleted$created" ON case_01.xref_p37_not_deleted USING btree (created);
CREATE INDEX "xref_p37_not_deleted$dbid" ON case_01.xref_p37_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p37_not_deleted$id" ON case_01.xref_p37_not_deleted USING btree (id);
CREATE INDEX "xref_p37_not_deleted$last" ON case_01.xref_p37_not_deleted USING btree (last);
CREATE INDEX "xref_p37_not_deleted$taxid" ON case_01.xref_p37_not_deleted USING btree (taxid);
CREATE INDEX "xref_p37_not_deleted$upi" ON case_01.xref_p37_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p38_deleted
CREATE TABLE case_01.xref_p38_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p38_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p38_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p38_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p38_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p38_deleted_deleted_urs_lookup ON case_01.xref_p38_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p38_deleted_upi_taxid_last ON case_01.xref_p38_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p38_deleted$ac" ON case_01.xref_p38_deleted USING btree (ac);
CREATE INDEX "xref_p38_deleted$created" ON case_01.xref_p38_deleted USING btree (created);
CREATE INDEX "xref_p38_deleted$dbid" ON case_01.xref_p38_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p38_deleted$id" ON case_01.xref_p38_deleted USING btree (id);
CREATE INDEX "xref_p38_deleted$last" ON case_01.xref_p38_deleted USING btree (last);
CREATE INDEX "xref_p38_deleted$taxid" ON case_01.xref_p38_deleted USING btree (taxid);
CREATE INDEX "xref_p38_deleted$upi" ON case_01.xref_p38_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p38_not_deleted
CREATE TABLE case_01.xref_p38_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p38_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p38_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p38_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p38_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p38_not_deleted_deleted_urs_lookup ON case_01.xref_p38_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p38_not_deleted_upi_taxid_last ON case_01.xref_p38_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p38_not_deleted$ac" ON case_01.xref_p38_not_deleted USING btree (ac);
CREATE INDEX "xref_p38_not_deleted$created" ON case_01.xref_p38_not_deleted USING btree (created);
CREATE INDEX "xref_p38_not_deleted$dbid" ON case_01.xref_p38_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p38_not_deleted$id" ON case_01.xref_p38_not_deleted USING btree (id);
CREATE INDEX "xref_p38_not_deleted$last" ON case_01.xref_p38_not_deleted USING btree (last);
CREATE INDEX "xref_p38_not_deleted$taxid" ON case_01.xref_p38_not_deleted USING btree (taxid);
CREATE INDEX "xref_p38_not_deleted$upi" ON case_01.xref_p38_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p39_deleted
CREATE TABLE case_01.xref_p39_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p39_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p39_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p39_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p39_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p39_deleted_deleted_urs_lookup ON case_01.xref_p39_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p39_deleted_upi_taxid_last ON case_01.xref_p39_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p39_deleted$ac" ON case_01.xref_p39_deleted USING btree (ac);
CREATE INDEX "xref_p39_deleted$created" ON case_01.xref_p39_deleted USING btree (created);
CREATE INDEX "xref_p39_deleted$dbid" ON case_01.xref_p39_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p39_deleted$id" ON case_01.xref_p39_deleted USING btree (id);
CREATE INDEX "xref_p39_deleted$last" ON case_01.xref_p39_deleted USING btree (last);
CREATE INDEX "xref_p39_deleted$taxid" ON case_01.xref_p39_deleted USING btree (taxid);
CREATE INDEX "xref_p39_deleted$upi" ON case_01.xref_p39_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p39_not_deleted
CREATE TABLE case_01.xref_p39_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p39_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p39_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p39_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p39_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p39_not_deleted_deleted_urs_lookup ON case_01.xref_p39_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p39_not_deleted_upi_taxid_last ON case_01.xref_p39_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p39_not_deleted$ac" ON case_01.xref_p39_not_deleted USING btree (ac);
CREATE INDEX "xref_p39_not_deleted$created" ON case_01.xref_p39_not_deleted USING btree (created);
CREATE INDEX "xref_p39_not_deleted$dbid" ON case_01.xref_p39_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p39_not_deleted$id" ON case_01.xref_p39_not_deleted USING btree (id);
CREATE INDEX "xref_p39_not_deleted$last" ON case_01.xref_p39_not_deleted USING btree (last);
CREATE INDEX "xref_p39_not_deleted$taxid" ON case_01.xref_p39_not_deleted USING btree (taxid);
CREATE INDEX "xref_p39_not_deleted$upi" ON case_01.xref_p39_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p3_deleted
CREATE TABLE case_01.xref_p3_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p3_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p3_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p3_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p3_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p3_deleted_deleted_urs_lookup ON case_01.xref_p3_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p3_deleted_upi_taxid_last ON case_01.xref_p3_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p3_deleted$ac" ON case_01.xref_p3_deleted USING btree (ac);
CREATE INDEX "xref_p3_deleted$created" ON case_01.xref_p3_deleted USING btree (created);
CREATE INDEX "xref_p3_deleted$dbid" ON case_01.xref_p3_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p3_deleted$id" ON case_01.xref_p3_deleted USING btree (id);
CREATE INDEX "xref_p3_deleted$last" ON case_01.xref_p3_deleted USING btree (last);
CREATE INDEX "xref_p3_deleted$taxid" ON case_01.xref_p3_deleted USING btree (taxid);
CREATE INDEX "xref_p3_deleted$upi" ON case_01.xref_p3_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p3_not_deleted
CREATE TABLE case_01.xref_p3_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p3_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p3_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p3_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p3_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p3_not_deleted_deleted_urs_lookup ON case_01.xref_p3_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p3_not_deleted_upi_taxid_last ON case_01.xref_p3_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p3_not_deleted$ac" ON case_01.xref_p3_not_deleted USING btree (ac);
CREATE INDEX "xref_p3_not_deleted$created" ON case_01.xref_p3_not_deleted USING btree (created);
CREATE INDEX "xref_p3_not_deleted$dbid" ON case_01.xref_p3_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p3_not_deleted$id" ON case_01.xref_p3_not_deleted USING btree (id);
CREATE INDEX "xref_p3_not_deleted$last" ON case_01.xref_p3_not_deleted USING btree (last);
CREATE INDEX "xref_p3_not_deleted$taxid" ON case_01.xref_p3_not_deleted USING btree (taxid);
CREATE INDEX "xref_p3_not_deleted$upi" ON case_01.xref_p3_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p40_deleted
CREATE TABLE case_01.xref_p40_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p40_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p40_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p40_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p40_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p40_deleted_deleted_urs_lookup ON case_01.xref_p40_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p40_deleted_upi_taxid_last ON case_01.xref_p40_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p40_deleted$ac" ON case_01.xref_p40_deleted USING btree (ac);
CREATE INDEX "xref_p40_deleted$created" ON case_01.xref_p40_deleted USING btree (created);
CREATE INDEX "xref_p40_deleted$dbid" ON case_01.xref_p40_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p40_deleted$id" ON case_01.xref_p40_deleted USING btree (id);
CREATE INDEX "xref_p40_deleted$last" ON case_01.xref_p40_deleted USING btree (last);
CREATE INDEX "xref_p40_deleted$taxid" ON case_01.xref_p40_deleted USING btree (taxid);
CREATE INDEX "xref_p40_deleted$upi" ON case_01.xref_p40_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p40_not_deleted
CREATE TABLE case_01.xref_p40_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p40_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p40_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p40_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p40_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p40_not_deleted_deleted_urs_lookup ON case_01.xref_p40_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p40_not_deleted_upi_taxid_last ON case_01.xref_p40_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p40_not_deleted$ac" ON case_01.xref_p40_not_deleted USING btree (ac);
CREATE INDEX "xref_p40_not_deleted$created" ON case_01.xref_p40_not_deleted USING btree (created);
CREATE INDEX "xref_p40_not_deleted$dbid" ON case_01.xref_p40_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p40_not_deleted$id" ON case_01.xref_p40_not_deleted USING btree (id);
CREATE INDEX "xref_p40_not_deleted$last" ON case_01.xref_p40_not_deleted USING btree (last);
CREATE INDEX "xref_p40_not_deleted$taxid" ON case_01.xref_p40_not_deleted USING btree (taxid);
CREATE INDEX "xref_p40_not_deleted$upi" ON case_01.xref_p40_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p41_deleted
CREATE TABLE case_01.xref_p41_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p41_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p41_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p41_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p41_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p41_deleted_deleted_urs_lookup ON case_01.xref_p41_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p41_deleted_upi_taxid_last ON case_01.xref_p41_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p41_deleted$ac" ON case_01.xref_p41_deleted USING btree (ac);
CREATE INDEX "xref_p41_deleted$created" ON case_01.xref_p41_deleted USING btree (created);
CREATE INDEX "xref_p41_deleted$dbid" ON case_01.xref_p41_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p41_deleted$id" ON case_01.xref_p41_deleted USING btree (id);
CREATE INDEX "xref_p41_deleted$last" ON case_01.xref_p41_deleted USING btree (last);
CREATE INDEX "xref_p41_deleted$taxid" ON case_01.xref_p41_deleted USING btree (taxid);
CREATE INDEX "xref_p41_deleted$upi" ON case_01.xref_p41_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p41_not_deleted
CREATE TABLE case_01.xref_p41_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p41_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p41_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p41_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p41_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p41_not_deleted_deleted_urs_lookup ON case_01.xref_p41_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p41_not_deleted_upi_taxid_last ON case_01.xref_p41_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p41_not_deleted$ac" ON case_01.xref_p41_not_deleted USING btree (ac);
CREATE INDEX "xref_p41_not_deleted$created" ON case_01.xref_p41_not_deleted USING btree (created);
CREATE INDEX "xref_p41_not_deleted$dbid" ON case_01.xref_p41_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p41_not_deleted$id" ON case_01.xref_p41_not_deleted USING btree (id);
CREATE INDEX "xref_p41_not_deleted$last" ON case_01.xref_p41_not_deleted USING btree (last);
CREATE INDEX "xref_p41_not_deleted$taxid" ON case_01.xref_p41_not_deleted USING btree (taxid);
CREATE INDEX "xref_p41_not_deleted$upi" ON case_01.xref_p41_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p42_deleted
CREATE TABLE case_01.xref_p42_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p42_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p42_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p42_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p42_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p42_deleted_deleted_urs_lookup ON case_01.xref_p42_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p42_deleted_upi_taxid_last ON case_01.xref_p42_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p42_deleted$ac" ON case_01.xref_p42_deleted USING btree (ac);
CREATE INDEX "xref_p42_deleted$created" ON case_01.xref_p42_deleted USING btree (created);
CREATE INDEX "xref_p42_deleted$dbid" ON case_01.xref_p42_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p42_deleted$id" ON case_01.xref_p42_deleted USING btree (id);
CREATE INDEX "xref_p42_deleted$last" ON case_01.xref_p42_deleted USING btree (last);
CREATE INDEX "xref_p42_deleted$taxid" ON case_01.xref_p42_deleted USING btree (taxid);
CREATE INDEX "xref_p42_deleted$upi" ON case_01.xref_p42_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p42_not_deleted
CREATE TABLE case_01.xref_p42_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p42_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p42_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p42_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p42_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p42_not_deleted_deleted_urs_lookup ON case_01.xref_p42_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p42_not_deleted_upi_taxid_last ON case_01.xref_p42_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p42_not_deleted$ac" ON case_01.xref_p42_not_deleted USING btree (ac);
CREATE INDEX "xref_p42_not_deleted$created" ON case_01.xref_p42_not_deleted USING btree (created);
CREATE INDEX "xref_p42_not_deleted$dbid" ON case_01.xref_p42_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p42_not_deleted$id" ON case_01.xref_p42_not_deleted USING btree (id);
CREATE INDEX "xref_p42_not_deleted$last" ON case_01.xref_p42_not_deleted USING btree (last);
CREATE INDEX "xref_p42_not_deleted$taxid" ON case_01.xref_p42_not_deleted USING btree (taxid);
CREATE INDEX "xref_p42_not_deleted$upi" ON case_01.xref_p42_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p43_deleted
CREATE TABLE case_01.xref_p43_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p43_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p43_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p43_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p43_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p43_deleted_deleted_urs_lookup ON case_01.xref_p43_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p43_deleted_upi_taxid_last ON case_01.xref_p43_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p43_deleted$ac" ON case_01.xref_p43_deleted USING btree (ac);
CREATE INDEX "xref_p43_deleted$created" ON case_01.xref_p43_deleted USING btree (created);
CREATE INDEX "xref_p43_deleted$dbid" ON case_01.xref_p43_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p43_deleted$id" ON case_01.xref_p43_deleted USING btree (id);
CREATE INDEX "xref_p43_deleted$last" ON case_01.xref_p43_deleted USING btree (last);
CREATE INDEX "xref_p43_deleted$taxid" ON case_01.xref_p43_deleted USING btree (taxid);
CREATE INDEX "xref_p43_deleted$upi" ON case_01.xref_p43_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p43_not_deleted
CREATE TABLE case_01.xref_p43_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p43_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p43_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p43_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p43_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p43_not_deleted_deleted_urs_lookup ON case_01.xref_p43_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p43_not_deleted_upi_taxid_last ON case_01.xref_p43_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p43_not_deleted$ac" ON case_01.xref_p43_not_deleted USING btree (ac);
CREATE INDEX "xref_p43_not_deleted$created" ON case_01.xref_p43_not_deleted USING btree (created);
CREATE INDEX "xref_p43_not_deleted$dbid" ON case_01.xref_p43_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p43_not_deleted$id" ON case_01.xref_p43_not_deleted USING btree (id);
CREATE INDEX "xref_p43_not_deleted$last" ON case_01.xref_p43_not_deleted USING btree (last);
CREATE INDEX "xref_p43_not_deleted$taxid" ON case_01.xref_p43_not_deleted USING btree (taxid);
CREATE INDEX "xref_p43_not_deleted$upi" ON case_01.xref_p43_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p44_deleted
CREATE TABLE case_01.xref_p44_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p44_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p44_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p44_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p44_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p44_deleted_deleted_urs_lookup ON case_01.xref_p44_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p44_deleted_upi_taxid_last ON case_01.xref_p44_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p44_deleted$ac" ON case_01.xref_p44_deleted USING btree (ac);
CREATE INDEX "xref_p44_deleted$created" ON case_01.xref_p44_deleted USING btree (created);
CREATE INDEX "xref_p44_deleted$dbid" ON case_01.xref_p44_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p44_deleted$id" ON case_01.xref_p44_deleted USING btree (id);
CREATE INDEX "xref_p44_deleted$last" ON case_01.xref_p44_deleted USING btree (last);
CREATE INDEX "xref_p44_deleted$taxid" ON case_01.xref_p44_deleted USING btree (taxid);
CREATE INDEX "xref_p44_deleted$upi" ON case_01.xref_p44_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p44_not_deleted
CREATE TABLE case_01.xref_p44_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p44_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p44_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p44_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p44_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p44_not_deleted_deleted_urs_lookup ON case_01.xref_p44_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p44_not_deleted_upi_taxid_last ON case_01.xref_p44_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p44_not_deleted$ac" ON case_01.xref_p44_not_deleted USING btree (ac);
CREATE INDEX "xref_p44_not_deleted$created" ON case_01.xref_p44_not_deleted USING btree (created);
CREATE INDEX "xref_p44_not_deleted$dbid" ON case_01.xref_p44_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p44_not_deleted$id" ON case_01.xref_p44_not_deleted USING btree (id);
CREATE INDEX "xref_p44_not_deleted$last" ON case_01.xref_p44_not_deleted USING btree (last);
CREATE INDEX "xref_p44_not_deleted$taxid" ON case_01.xref_p44_not_deleted USING btree (taxid);
CREATE INDEX "xref_p44_not_deleted$upi" ON case_01.xref_p44_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p45_deleted
CREATE TABLE case_01.xref_p45_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p45_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p45_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p45_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p45_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p45_deleted_deleted_urs_lookup ON case_01.xref_p45_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p45_deleted_upi_taxid_last ON case_01.xref_p45_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p45_deleted$ac" ON case_01.xref_p45_deleted USING btree (ac);
CREATE INDEX "xref_p45_deleted$created" ON case_01.xref_p45_deleted USING btree (created);
CREATE INDEX "xref_p45_deleted$dbid" ON case_01.xref_p45_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p45_deleted$id" ON case_01.xref_p45_deleted USING btree (id);
CREATE INDEX "xref_p45_deleted$last" ON case_01.xref_p45_deleted USING btree (last);
CREATE INDEX "xref_p45_deleted$taxid" ON case_01.xref_p45_deleted USING btree (taxid);
CREATE INDEX "xref_p45_deleted$upi" ON case_01.xref_p45_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p45_not_deleted
CREATE TABLE case_01.xref_p45_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p45_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p45_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p45_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p45_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p45_not_deleted_deleted_urs_lookup ON case_01.xref_p45_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p45_not_deleted_upi_taxid_last ON case_01.xref_p45_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p45_not_deleted$ac" ON case_01.xref_p45_not_deleted USING btree (ac);
CREATE INDEX "xref_p45_not_deleted$created" ON case_01.xref_p45_not_deleted USING btree (created);
CREATE INDEX "xref_p45_not_deleted$dbid" ON case_01.xref_p45_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p45_not_deleted$id" ON case_01.xref_p45_not_deleted USING btree (id);
CREATE INDEX "xref_p45_not_deleted$last" ON case_01.xref_p45_not_deleted USING btree (last);
CREATE INDEX "xref_p45_not_deleted$taxid" ON case_01.xref_p45_not_deleted USING btree (taxid);
CREATE INDEX "xref_p45_not_deleted$upi" ON case_01.xref_p45_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p46_deleted
CREATE TABLE case_01.xref_p46_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p46_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p46_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p46_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p46_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p46_deleted_deleted_urs_lookup ON case_01.xref_p46_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p46_deleted_upi_taxid_last ON case_01.xref_p46_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p46_deleted$ac" ON case_01.xref_p46_deleted USING btree (ac);
CREATE INDEX "xref_p46_deleted$created" ON case_01.xref_p46_deleted USING btree (created);
CREATE INDEX "xref_p46_deleted$dbid" ON case_01.xref_p46_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p46_deleted$id" ON case_01.xref_p46_deleted USING btree (id);
CREATE INDEX "xref_p46_deleted$last" ON case_01.xref_p46_deleted USING btree (last);
CREATE INDEX "xref_p46_deleted$taxid" ON case_01.xref_p46_deleted USING btree (taxid);
CREATE INDEX "xref_p46_deleted$upi" ON case_01.xref_p46_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p46_not_deleted
CREATE TABLE case_01.xref_p46_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p46_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p46_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p46_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p46_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p46_not_deleted_deleted_urs_lookup ON case_01.xref_p46_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p46_not_deleted_upi_taxid_last ON case_01.xref_p46_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p46_not_deleted$ac" ON case_01.xref_p46_not_deleted USING btree (ac);
CREATE INDEX "xref_p46_not_deleted$created" ON case_01.xref_p46_not_deleted USING btree (created);
CREATE INDEX "xref_p46_not_deleted$dbid" ON case_01.xref_p46_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p46_not_deleted$id" ON case_01.xref_p46_not_deleted USING btree (id);
CREATE INDEX "xref_p46_not_deleted$last" ON case_01.xref_p46_not_deleted USING btree (last);
CREATE INDEX "xref_p46_not_deleted$taxid" ON case_01.xref_p46_not_deleted USING btree (taxid);
CREATE INDEX "xref_p46_not_deleted$upi" ON case_01.xref_p46_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p47_deleted
CREATE TABLE case_01.xref_p47_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p47_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p47_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p47_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p47_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p47_deleted_deleted_urs_lookup ON case_01.xref_p47_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p47_deleted_upi_taxid_last ON case_01.xref_p47_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p47_deleted$ac" ON case_01.xref_p47_deleted USING btree (ac);
CREATE INDEX "xref_p47_deleted$created" ON case_01.xref_p47_deleted USING btree (created);
CREATE INDEX "xref_p47_deleted$dbid" ON case_01.xref_p47_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p47_deleted$id" ON case_01.xref_p47_deleted USING btree (id);
CREATE INDEX "xref_p47_deleted$last" ON case_01.xref_p47_deleted USING btree (last);
CREATE INDEX "xref_p47_deleted$taxid" ON case_01.xref_p47_deleted USING btree (taxid);
CREATE INDEX "xref_p47_deleted$upi" ON case_01.xref_p47_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p47_not_deleted
CREATE TABLE case_01.xref_p47_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p47_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p47_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p47_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p47_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p47_not_deleted_deleted_urs_lookup ON case_01.xref_p47_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p47_not_deleted_upi_taxid_last ON case_01.xref_p47_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p47_not_deleted$ac" ON case_01.xref_p47_not_deleted USING btree (ac);
CREATE INDEX "xref_p47_not_deleted$created" ON case_01.xref_p47_not_deleted USING btree (created);
CREATE INDEX "xref_p47_not_deleted$dbid" ON case_01.xref_p47_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p47_not_deleted$id" ON case_01.xref_p47_not_deleted USING btree (id);
CREATE INDEX "xref_p47_not_deleted$last" ON case_01.xref_p47_not_deleted USING btree (last);
CREATE INDEX "xref_p47_not_deleted$taxid" ON case_01.xref_p47_not_deleted USING btree (taxid);
CREATE INDEX "xref_p47_not_deleted$upi" ON case_01.xref_p47_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p48_deleted
CREATE TABLE case_01.xref_p48_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p48_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p48_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p48_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p48_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p48_deleted_deleted_urs_lookup ON case_01.xref_p48_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p48_deleted_upi_taxid_last ON case_01.xref_p48_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p48_deleted$ac" ON case_01.xref_p48_deleted USING btree (ac);
CREATE INDEX "xref_p48_deleted$created" ON case_01.xref_p48_deleted USING btree (created);
CREATE INDEX "xref_p48_deleted$dbid" ON case_01.xref_p48_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p48_deleted$id" ON case_01.xref_p48_deleted USING btree (id);
CREATE INDEX "xref_p48_deleted$last" ON case_01.xref_p48_deleted USING btree (last);
CREATE INDEX "xref_p48_deleted$taxid" ON case_01.xref_p48_deleted USING btree (taxid);
CREATE INDEX "xref_p48_deleted$upi" ON case_01.xref_p48_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p48_not_deleted
CREATE TABLE case_01.xref_p48_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p48_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p48_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p48_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p48_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p48_not_deleted_deleted_urs_lookup ON case_01.xref_p48_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p48_not_deleted_upi_taxid_last ON case_01.xref_p48_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p48_not_deleted$ac" ON case_01.xref_p48_not_deleted USING btree (ac);
CREATE INDEX "xref_p48_not_deleted$created" ON case_01.xref_p48_not_deleted USING btree (created);
CREATE INDEX "xref_p48_not_deleted$dbid" ON case_01.xref_p48_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p48_not_deleted$id" ON case_01.xref_p48_not_deleted USING btree (id);
CREATE INDEX "xref_p48_not_deleted$last" ON case_01.xref_p48_not_deleted USING btree (last);
CREATE INDEX "xref_p48_not_deleted$taxid" ON case_01.xref_p48_not_deleted USING btree (taxid);
CREATE INDEX "xref_p48_not_deleted$upi" ON case_01.xref_p48_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p49_deleted
CREATE TABLE case_01.xref_p49_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p49_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p49_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p49_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p49_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p49_deleted_deleted_urs_lookup ON case_01.xref_p49_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p49_deleted_upi_taxid_last ON case_01.xref_p49_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p49_deleted$ac" ON case_01.xref_p49_deleted USING btree (ac);
CREATE INDEX "xref_p49_deleted$created" ON case_01.xref_p49_deleted USING btree (created);
CREATE INDEX "xref_p49_deleted$dbid" ON case_01.xref_p49_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p49_deleted$id" ON case_01.xref_p49_deleted USING btree (id);
CREATE INDEX "xref_p49_deleted$last" ON case_01.xref_p49_deleted USING btree (last);
CREATE INDEX "xref_p49_deleted$taxid" ON case_01.xref_p49_deleted USING btree (taxid);
CREATE INDEX "xref_p49_deleted$upi" ON case_01.xref_p49_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p49_not_deleted
CREATE TABLE case_01.xref_p49_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p49_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p49_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p49_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p49_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p49_not_deleted_deleted_urs_lookup ON case_01.xref_p49_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p49_not_deleted_upi_taxid_last ON case_01.xref_p49_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p49_not_deleted$ac" ON case_01.xref_p49_not_deleted USING btree (ac);
CREATE INDEX "xref_p49_not_deleted$created" ON case_01.xref_p49_not_deleted USING btree (created);
CREATE INDEX "xref_p49_not_deleted$dbid" ON case_01.xref_p49_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p49_not_deleted$id" ON case_01.xref_p49_not_deleted USING btree (id);
CREATE INDEX "xref_p49_not_deleted$last" ON case_01.xref_p49_not_deleted USING btree (last);
CREATE INDEX "xref_p49_not_deleted$taxid" ON case_01.xref_p49_not_deleted USING btree (taxid);
CREATE INDEX "xref_p49_not_deleted$upi" ON case_01.xref_p49_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p4_deleted
CREATE TABLE case_01.xref_p4_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p4_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p4_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p4_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p4_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p4_deleted_deleted_urs_lookup ON case_01.xref_p4_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p4_deleted_upi_taxid_last ON case_01.xref_p4_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p4_deleted$ac" ON case_01.xref_p4_deleted USING btree (ac);
CREATE INDEX "xref_p4_deleted$created" ON case_01.xref_p4_deleted USING btree (created);
CREATE INDEX "xref_p4_deleted$dbid" ON case_01.xref_p4_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p4_deleted$id" ON case_01.xref_p4_deleted USING btree (id);
CREATE INDEX "xref_p4_deleted$last" ON case_01.xref_p4_deleted USING btree (last);
CREATE INDEX "xref_p4_deleted$taxid" ON case_01.xref_p4_deleted USING btree (taxid);
CREATE INDEX "xref_p4_deleted$upi" ON case_01.xref_p4_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p4_not_deleted
CREATE TABLE case_01.xref_p4_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p4_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p4_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p4_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p4_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p4_not_deleted_deleted_urs_lookup ON case_01.xref_p4_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p4_not_deleted_upi_taxid_last ON case_01.xref_p4_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p4_not_deleted$ac" ON case_01.xref_p4_not_deleted USING btree (ac);
CREATE INDEX "xref_p4_not_deleted$created" ON case_01.xref_p4_not_deleted USING btree (created);
CREATE INDEX "xref_p4_not_deleted$dbid" ON case_01.xref_p4_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p4_not_deleted$id" ON case_01.xref_p4_not_deleted USING btree (id);
CREATE INDEX "xref_p4_not_deleted$last" ON case_01.xref_p4_not_deleted USING btree (last);
CREATE INDEX "xref_p4_not_deleted$taxid" ON case_01.xref_p4_not_deleted USING btree (taxid);
CREATE INDEX "xref_p4_not_deleted$upi" ON case_01.xref_p4_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p50_deleted
CREATE TABLE case_01.xref_p50_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p50_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p50_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p50_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p50_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p50_deleted_deleted_urs_lookup ON case_01.xref_p50_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p50_deleted_upi_taxid_last ON case_01.xref_p50_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p50_deleted$ac" ON case_01.xref_p50_deleted USING btree (ac);
CREATE INDEX "xref_p50_deleted$created" ON case_01.xref_p50_deleted USING btree (created);
CREATE INDEX "xref_p50_deleted$dbid" ON case_01.xref_p50_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p50_deleted$id" ON case_01.xref_p50_deleted USING btree (id);
CREATE INDEX "xref_p50_deleted$last" ON case_01.xref_p50_deleted USING btree (last);
CREATE INDEX "xref_p50_deleted$taxid" ON case_01.xref_p50_deleted USING btree (taxid);
CREATE INDEX "xref_p50_deleted$upi" ON case_01.xref_p50_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p50_not_deleted
CREATE TABLE case_01.xref_p50_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p50_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p50_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p50_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p50_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p50_not_deleted_deleted_urs_lookup ON case_01.xref_p50_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p50_not_deleted_upi_taxid_last ON case_01.xref_p50_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p50_not_deleted$ac" ON case_01.xref_p50_not_deleted USING btree (ac);
CREATE INDEX "xref_p50_not_deleted$created" ON case_01.xref_p50_not_deleted USING btree (created);
CREATE INDEX "xref_p50_not_deleted$dbid" ON case_01.xref_p50_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p50_not_deleted$id" ON case_01.xref_p50_not_deleted USING btree (id);
CREATE INDEX "xref_p50_not_deleted$last" ON case_01.xref_p50_not_deleted USING btree (last);
CREATE INDEX "xref_p50_not_deleted$taxid" ON case_01.xref_p50_not_deleted USING btree (taxid);
CREATE INDEX "xref_p50_not_deleted$upi" ON case_01.xref_p50_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p51_deleted
CREATE TABLE case_01.xref_p51_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p51_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p51_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p51_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p51_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p51_deleted_deleted_urs_lookup ON case_01.xref_p51_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p51_deleted_upi_taxid_last ON case_01.xref_p51_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p51_deleted$ac" ON case_01.xref_p51_deleted USING btree (ac);
CREATE INDEX "xref_p51_deleted$created" ON case_01.xref_p51_deleted USING btree (created);
CREATE INDEX "xref_p51_deleted$dbid" ON case_01.xref_p51_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p51_deleted$id" ON case_01.xref_p51_deleted USING btree (id);
CREATE INDEX "xref_p51_deleted$last" ON case_01.xref_p51_deleted USING btree (last);
CREATE INDEX "xref_p51_deleted$taxid" ON case_01.xref_p51_deleted USING btree (taxid);
CREATE INDEX "xref_p51_deleted$upi" ON case_01.xref_p51_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p51_not_deleted
CREATE TABLE case_01.xref_p51_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p51_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p51_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p51_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p51_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p51_not_deleted_deleted_urs_lookup ON case_01.xref_p51_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p51_not_deleted_upi_taxid_last ON case_01.xref_p51_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p51_not_deleted$ac" ON case_01.xref_p51_not_deleted USING btree (ac);
CREATE INDEX "xref_p51_not_deleted$created" ON case_01.xref_p51_not_deleted USING btree (created);
CREATE INDEX "xref_p51_not_deleted$dbid" ON case_01.xref_p51_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p51_not_deleted$id" ON case_01.xref_p51_not_deleted USING btree (id);
CREATE INDEX "xref_p51_not_deleted$last" ON case_01.xref_p51_not_deleted USING btree (last);
CREATE INDEX "xref_p51_not_deleted$taxid" ON case_01.xref_p51_not_deleted USING btree (taxid);
CREATE INDEX "xref_p51_not_deleted$upi" ON case_01.xref_p51_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p52_deleted
CREATE TABLE case_01.xref_p52_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p52_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p52_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p52_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p52_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p52_deleted_deleted_urs_lookup ON case_01.xref_p52_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p52_deleted_upi_taxid_last ON case_01.xref_p52_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p52_deleted$ac" ON case_01.xref_p52_deleted USING btree (ac);
CREATE INDEX "xref_p52_deleted$created" ON case_01.xref_p52_deleted USING btree (created);
CREATE INDEX "xref_p52_deleted$dbid" ON case_01.xref_p52_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p52_deleted$id" ON case_01.xref_p52_deleted USING btree (id);
CREATE INDEX "xref_p52_deleted$last" ON case_01.xref_p52_deleted USING btree (last);
CREATE INDEX "xref_p52_deleted$taxid" ON case_01.xref_p52_deleted USING btree (taxid);
CREATE INDEX "xref_p52_deleted$upi" ON case_01.xref_p52_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p52_not_deleted
CREATE TABLE case_01.xref_p52_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p52_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p52_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p52_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p52_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p52_not_deleted_deleted_urs_lookup ON case_01.xref_p52_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p52_not_deleted_upi_taxid_last ON case_01.xref_p52_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p52_not_deleted$ac" ON case_01.xref_p52_not_deleted USING btree (ac);
CREATE INDEX "xref_p52_not_deleted$created" ON case_01.xref_p52_not_deleted USING btree (created);
CREATE INDEX "xref_p52_not_deleted$dbid" ON case_01.xref_p52_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p52_not_deleted$id" ON case_01.xref_p52_not_deleted USING btree (id);
CREATE INDEX "xref_p52_not_deleted$last" ON case_01.xref_p52_not_deleted USING btree (last);
CREATE INDEX "xref_p52_not_deleted$taxid" ON case_01.xref_p52_not_deleted USING btree (taxid);
CREATE INDEX "xref_p52_not_deleted$upi" ON case_01.xref_p52_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p53_deleted
CREATE TABLE case_01.xref_p53_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p53_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p53_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p53_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p53_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p53_deleted_deleted_urs_lookup ON case_01.xref_p53_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p53_deleted_upi_taxid_last ON case_01.xref_p53_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p53_deleted$ac" ON case_01.xref_p53_deleted USING btree (ac);
CREATE INDEX "xref_p53_deleted$created" ON case_01.xref_p53_deleted USING btree (created);
CREATE INDEX "xref_p53_deleted$dbid" ON case_01.xref_p53_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p53_deleted$id" ON case_01.xref_p53_deleted USING btree (id);
CREATE INDEX "xref_p53_deleted$last" ON case_01.xref_p53_deleted USING btree (last);
CREATE INDEX "xref_p53_deleted$taxid" ON case_01.xref_p53_deleted USING btree (taxid);
CREATE INDEX "xref_p53_deleted$upi" ON case_01.xref_p53_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p53_not_deleted
CREATE TABLE case_01.xref_p53_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p53_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p53_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p53_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p53_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p53_not_deleted_deleted_urs_lookup ON case_01.xref_p53_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p53_not_deleted_upi_taxid_last ON case_01.xref_p53_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p53_not_deleted$ac" ON case_01.xref_p53_not_deleted USING btree (ac);
CREATE INDEX "xref_p53_not_deleted$created" ON case_01.xref_p53_not_deleted USING btree (created);
CREATE INDEX "xref_p53_not_deleted$dbid" ON case_01.xref_p53_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p53_not_deleted$id" ON case_01.xref_p53_not_deleted USING btree (id);
CREATE INDEX "xref_p53_not_deleted$last" ON case_01.xref_p53_not_deleted USING btree (last);
CREATE INDEX "xref_p53_not_deleted$taxid" ON case_01.xref_p53_not_deleted USING btree (taxid);
CREATE INDEX "xref_p53_not_deleted$upi" ON case_01.xref_p53_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p54_deleted
CREATE TABLE case_01.xref_p54_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_p54_deleted_id_seq'::regclass) NOT NULL,
  CONSTRAINT xref_p54_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p54_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p54_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p54_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p54_deleted_deleted_urs_lookup ON case_01.xref_p54_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p54_deleted_upi_taxid_last ON case_01.xref_p54_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p54_deleted$ac" ON case_01.xref_p54_deleted USING btree (ac);
CREATE INDEX "xref_p54_deleted$created" ON case_01.xref_p54_deleted USING btree (created);
CREATE INDEX "xref_p54_deleted$dbid" ON case_01.xref_p54_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p54_deleted$id" ON case_01.xref_p54_deleted USING btree (id);
CREATE INDEX "xref_p54_deleted$last" ON case_01.xref_p54_deleted USING btree (last);
CREATE INDEX "xref_p54_deleted$taxid" ON case_01.xref_p54_deleted USING btree (taxid);
CREATE INDEX "xref_p54_deleted$upi" ON case_01.xref_p54_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p54_not_deleted
CREATE TABLE case_01.xref_p54_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_p54_not_deleted_id_seq'::regclass) NOT NULL,
  CONSTRAINT xref_p54_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p54_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p54_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p54_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p54_not_deleted_deleted_urs_lookup ON case_01.xref_p54_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p54_not_deleted_upi_taxid_last ON case_01.xref_p54_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p54_not_deleted$ac" ON case_01.xref_p54_not_deleted USING btree (ac);
CREATE INDEX "xref_p54_not_deleted$created" ON case_01.xref_p54_not_deleted USING btree (created);
CREATE INDEX "xref_p54_not_deleted$dbid" ON case_01.xref_p54_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p54_not_deleted$id" ON case_01.xref_p54_not_deleted USING btree (id);
CREATE INDEX "xref_p54_not_deleted$last" ON case_01.xref_p54_not_deleted USING btree (last);
CREATE INDEX "xref_p54_not_deleted$taxid" ON case_01.xref_p54_not_deleted USING btree (taxid);
CREATE INDEX "xref_p54_not_deleted$upi" ON case_01.xref_p54_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p55_deleted
CREATE TABLE case_01.xref_p55_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p55_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p55_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p55_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p55_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p55_deleted_deleted_urs_lookup ON case_01.xref_p55_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p55_deleted_upi_taxid_last ON case_01.xref_p55_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p55_deleted$ac" ON case_01.xref_p55_deleted USING btree (ac);
CREATE INDEX "xref_p55_deleted$created" ON case_01.xref_p55_deleted USING btree (created);
CREATE INDEX "xref_p55_deleted$dbid" ON case_01.xref_p55_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p55_deleted$id" ON case_01.xref_p55_deleted USING btree (id);
CREATE INDEX "xref_p55_deleted$last" ON case_01.xref_p55_deleted USING btree (last);
CREATE INDEX "xref_p55_deleted$taxid" ON case_01.xref_p55_deleted USING btree (taxid);
CREATE INDEX "xref_p55_deleted$upi" ON case_01.xref_p55_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p55_not_deleted
CREATE TABLE case_01.xref_p55_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p55_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p55_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p55_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p55_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p55_not_deleted_deleted_urs_lookup ON case_01.xref_p55_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p55_not_deleted_upi_taxid_last ON case_01.xref_p55_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p55_not_deleted$ac" ON case_01.xref_p55_not_deleted USING btree (ac);
CREATE INDEX "xref_p55_not_deleted$created" ON case_01.xref_p55_not_deleted USING btree (created);
CREATE INDEX "xref_p55_not_deleted$dbid" ON case_01.xref_p55_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p55_not_deleted$id" ON case_01.xref_p55_not_deleted USING btree (id);
CREATE INDEX "xref_p55_not_deleted$last" ON case_01.xref_p55_not_deleted USING btree (last);
CREATE INDEX "xref_p55_not_deleted$taxid" ON case_01.xref_p55_not_deleted USING btree (taxid);
CREATE INDEX "xref_p55_not_deleted$upi" ON case_01.xref_p55_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p56_deleted
CREATE TABLE case_01.xref_p56_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_p56_deleted_id_seq'::regclass) NOT NULL,
  CONSTRAINT xref_p56_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p56_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p56_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p56_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p56_deleted_deleted_urs_lookup ON case_01.xref_p56_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p56_deleted_upi_taxid_last ON case_01.xref_p56_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p56_deleted$ac" ON case_01.xref_p56_deleted USING btree (ac);
CREATE INDEX "xref_p56_deleted$created" ON case_01.xref_p56_deleted USING btree (created);
CREATE INDEX "xref_p56_deleted$dbid" ON case_01.xref_p56_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p56_deleted$id" ON case_01.xref_p56_deleted USING btree (id);
CREATE INDEX "xref_p56_deleted$last" ON case_01.xref_p56_deleted USING btree (last);
CREATE INDEX "xref_p56_deleted$taxid" ON case_01.xref_p56_deleted USING btree (taxid);
CREATE INDEX "xref_p56_deleted$upi" ON case_01.xref_p56_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p56_not_deleted
CREATE TABLE case_01.xref_p56_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_p56_not_deleted_id_seq'::regclass) NOT NULL,
  CONSTRAINT xref_p56_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p56_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p56_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p56_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p56_not_deleted_deleted_urs_lookup ON case_01.xref_p56_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p56_not_deleted_upi_taxid_last ON case_01.xref_p56_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p56_not_deleted$ac" ON case_01.xref_p56_not_deleted USING btree (ac);
CREATE INDEX "xref_p56_not_deleted$created" ON case_01.xref_p56_not_deleted USING btree (created);
CREATE INDEX "xref_p56_not_deleted$dbid" ON case_01.xref_p56_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p56_not_deleted$id" ON case_01.xref_p56_not_deleted USING btree (id);
CREATE INDEX "xref_p56_not_deleted$last" ON case_01.xref_p56_not_deleted USING btree (last);
CREATE INDEX "xref_p56_not_deleted$taxid" ON case_01.xref_p56_not_deleted USING btree (taxid);
CREATE INDEX "xref_p56_not_deleted$upi" ON case_01.xref_p56_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p5_deleted
CREATE TABLE case_01.xref_p5_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p5_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p5_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p5_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p5_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p5_deleted_deleted_urs_lookup ON case_01.xref_p5_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p5_deleted_upi_taxid_last ON case_01.xref_p5_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p5_deleted__upi_taxid ON case_01.xref_p5_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p5_deleted$ac" ON case_01.xref_p5_deleted USING btree (ac);
CREATE INDEX "xref_p5_deleted$created" ON case_01.xref_p5_deleted USING btree (created);
CREATE INDEX "xref_p5_deleted$dbid" ON case_01.xref_p5_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p5_deleted$id" ON case_01.xref_p5_deleted USING btree (id);
CREATE INDEX "xref_p5_deleted$last" ON case_01.xref_p5_deleted USING btree (last);
CREATE INDEX "xref_p5_deleted$taxid" ON case_01.xref_p5_deleted USING btree (taxid);
CREATE INDEX "xref_p5_deleted$upi" ON case_01.xref_p5_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p5_not_deleted
CREATE TABLE case_01.xref_p5_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p5_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p5_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id) DEFERRABLE,
  CONSTRAINT xref_p5_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id) DEFERRABLE,
  CONSTRAINT xref_p5_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi) DEFERRABLE
);
CREATE INDEX idx_xref_p5_not_deleted_deleted_urs_lookup ON case_01.xref_p5_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p5_not_deleted_upi_taxid_last ON case_01.xref_p5_not_deleted USING btree (upi, taxid, last);
CREATE INDEX ix_xref_p5_not_deleted__upi_taxid ON case_01.xref_p5_not_deleted USING btree (upi, taxid);
CREATE INDEX "xref_p5_not_deleted$ac" ON case_01.xref_p5_not_deleted USING btree (ac);
CREATE INDEX "xref_p5_not_deleted$created" ON case_01.xref_p5_not_deleted USING btree (created);
CREATE INDEX "xref_p5_not_deleted$dbid" ON case_01.xref_p5_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p5_not_deleted$id" ON case_01.xref_p5_not_deleted USING btree (id);
CREATE INDEX "xref_p5_not_deleted$last" ON case_01.xref_p5_not_deleted USING btree (last);
CREATE INDEX "xref_p5_not_deleted$taxid" ON case_01.xref_p5_not_deleted USING btree (taxid);
CREATE INDEX "xref_p5_not_deleted$upi" ON case_01.xref_p5_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p6_deleted
CREATE TABLE case_01.xref_p6_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p6_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p6_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p6_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p6_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p6_deleted_deleted_urs_lookup ON case_01.xref_p6_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p6_deleted_upi_taxid_last ON case_01.xref_p6_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p6_deleted$ac" ON case_01.xref_p6_deleted USING btree (ac);
CREATE INDEX "xref_p6_deleted$created" ON case_01.xref_p6_deleted USING btree (created);
CREATE INDEX "xref_p6_deleted$dbid" ON case_01.xref_p6_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p6_deleted$id" ON case_01.xref_p6_deleted USING btree (id);
CREATE INDEX "xref_p6_deleted$last" ON case_01.xref_p6_deleted USING btree (last);
CREATE INDEX "xref_p6_deleted$taxid" ON case_01.xref_p6_deleted USING btree (taxid);
CREATE INDEX "xref_p6_deleted$upi" ON case_01.xref_p6_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p6_not_deleted
CREATE TABLE case_01.xref_p6_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p6_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p6_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p6_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p6_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p6_not_deleted_deleted_urs_lookup ON case_01.xref_p6_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p6_not_deleted_upi_taxid_last ON case_01.xref_p6_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p6_not_deleted$ac" ON case_01.xref_p6_not_deleted USING btree (ac);
CREATE INDEX "xref_p6_not_deleted$created" ON case_01.xref_p6_not_deleted USING btree (created);
CREATE INDEX "xref_p6_not_deleted$dbid" ON case_01.xref_p6_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p6_not_deleted$id" ON case_01.xref_p6_not_deleted USING btree (id);
CREATE INDEX "xref_p6_not_deleted$last" ON case_01.xref_p6_not_deleted USING btree (last);
CREATE INDEX "xref_p6_not_deleted$taxid" ON case_01.xref_p6_not_deleted USING btree (taxid);
CREATE INDEX "xref_p6_not_deleted$upi" ON case_01.xref_p6_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p7_deleted
CREATE TABLE case_01.xref_p7_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p7_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p7_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p7_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p7_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p7_deleted_deleted_urs_lookup ON case_01.xref_p7_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p7_deleted_upi_taxid_last ON case_01.xref_p7_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p7_deleted$ac" ON case_01.xref_p7_deleted USING btree (ac);
CREATE INDEX "xref_p7_deleted$created" ON case_01.xref_p7_deleted USING btree (created);
CREATE INDEX "xref_p7_deleted$dbid" ON case_01.xref_p7_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p7_deleted$id" ON case_01.xref_p7_deleted USING btree (id);
CREATE INDEX "xref_p7_deleted$last" ON case_01.xref_p7_deleted USING btree (last);
CREATE INDEX "xref_p7_deleted$taxid" ON case_01.xref_p7_deleted USING btree (taxid);
CREATE INDEX "xref_p7_deleted$upi" ON case_01.xref_p7_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p7_not_deleted
CREATE TABLE case_01.xref_p7_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p7_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p7_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p7_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p7_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p7_not_deleted_deleted_urs_lookup ON case_01.xref_p7_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p7_not_deleted_upi_taxid_last ON case_01.xref_p7_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p7_not_deleted$ac" ON case_01.xref_p7_not_deleted USING btree (ac);
CREATE INDEX "xref_p7_not_deleted$created" ON case_01.xref_p7_not_deleted USING btree (created);
CREATE INDEX "xref_p7_not_deleted$dbid" ON case_01.xref_p7_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p7_not_deleted$id" ON case_01.xref_p7_not_deleted USING btree (id);
CREATE INDEX "xref_p7_not_deleted$last" ON case_01.xref_p7_not_deleted USING btree (last);
CREATE INDEX "xref_p7_not_deleted$taxid" ON case_01.xref_p7_not_deleted USING btree (taxid);
CREATE INDEX "xref_p7_not_deleted$upi" ON case_01.xref_p7_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p8_deleted
CREATE TABLE case_01.xref_p8_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p8_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p8_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p8_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p8_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p8_deleted_deleted_urs_lookup ON case_01.xref_p8_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p8_deleted_upi_taxid_last ON case_01.xref_p8_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p8_deleted$ac" ON case_01.xref_p8_deleted USING btree (ac);
CREATE INDEX "xref_p8_deleted$created" ON case_01.xref_p8_deleted USING btree (created);
CREATE INDEX "xref_p8_deleted$dbid" ON case_01.xref_p8_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p8_deleted$id" ON case_01.xref_p8_deleted USING btree (id);
CREATE INDEX "xref_p8_deleted$last" ON case_01.xref_p8_deleted USING btree (last);
CREATE INDEX "xref_p8_deleted$taxid" ON case_01.xref_p8_deleted USING btree (taxid);
CREATE INDEX "xref_p8_deleted$upi" ON case_01.xref_p8_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p8_not_deleted
CREATE TABLE case_01.xref_p8_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p8_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p8_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p8_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p8_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p8_not_deleted_deleted_urs_lookup ON case_01.xref_p8_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p8_not_deleted_upi_taxid_last ON case_01.xref_p8_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p8_not_deleted$ac" ON case_01.xref_p8_not_deleted USING btree (ac);
CREATE INDEX "xref_p8_not_deleted$created" ON case_01.xref_p8_not_deleted USING btree (created);
CREATE INDEX "xref_p8_not_deleted$dbid" ON case_01.xref_p8_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p8_not_deleted$id" ON case_01.xref_p8_not_deleted USING btree (id);
CREATE INDEX "xref_p8_not_deleted$last" ON case_01.xref_p8_not_deleted USING btree (last);
CREATE INDEX "xref_p8_not_deleted$taxid" ON case_01.xref_p8_not_deleted USING btree (taxid);
CREATE INDEX "xref_p8_not_deleted$upi" ON case_01.xref_p8_not_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p9_deleted
CREATE TABLE case_01.xref_p9_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p9_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p9_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p9_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p9_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p9_deleted_deleted_urs_lookup ON case_01.xref_p9_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p9_deleted_upi_taxid_last ON case_01.xref_p9_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p9_deleted$ac" ON case_01.xref_p9_deleted USING btree (ac);
CREATE INDEX "xref_p9_deleted$created" ON case_01.xref_p9_deleted USING btree (created);
CREATE INDEX "xref_p9_deleted$dbid" ON case_01.xref_p9_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p9_deleted$id" ON case_01.xref_p9_deleted USING btree (id);
CREATE INDEX "xref_p9_deleted$last" ON case_01.xref_p9_deleted USING btree (last);
CREATE INDEX "xref_p9_deleted$taxid" ON case_01.xref_p9_deleted USING btree (taxid);
CREATE INDEX "xref_p9_deleted$upi" ON case_01.xref_p9_deleted USING btree (upi);

-- relation-detector-fixture-table: case_01.xref_p9_not_deleted
CREATE TABLE case_01.xref_p9_not_deleted (
  dbid smallint NOT NULL,
  created integer NOT NULL,
  last integer NOT NULL,
  upi character varying(26) NOT NULL,
  version_i integer NOT NULL,
  deleted character(1) NOT NULL,
  timestamp timestamp without time zone DEFAULT ('now'::text)::timestamp without time zone NOT NULL,
  userstamp character varying(20) DEFAULT 'USER'::character varying NOT NULL,
  ac character varying(300) NOT NULL,
  version integer,
  taxid bigint NOT NULL,
  id bigint DEFAULT nextval('xref_pk_seq'::regclass),
  CONSTRAINT xref_p9_not_deleted_fk1 FOREIGN KEY (created) REFERENCES rnc_release(id),
  CONSTRAINT xref_p9_not_deleted_fk2 FOREIGN KEY (dbid) REFERENCES rnc_database(id),
  CONSTRAINT xref_p9_not_deleted_fk3 FOREIGN KEY (last) REFERENCES rnc_release(id),
  CONSTRAINT xref_p9_not_deleted_fk4 FOREIGN KEY (upi) REFERENCES rna(upi)
);
CREATE INDEX idx_xref_p9_not_deleted_deleted_urs_lookup ON case_01.xref_p9_not_deleted USING btree (deleted, ((((upi)::text || '_'::text) || taxid)));
CREATE INDEX idx_xref_p9_not_deleted_upi_taxid_last ON case_01.xref_p9_not_deleted USING btree (upi, taxid, last);
CREATE INDEX "xref_p9_not_deleted$ac" ON case_01.xref_p9_not_deleted USING btree (ac);
CREATE INDEX "xref_p9_not_deleted$created" ON case_01.xref_p9_not_deleted USING btree (created);
CREATE INDEX "xref_p9_not_deleted$dbid" ON case_01.xref_p9_not_deleted USING btree (dbid);
CREATE UNIQUE INDEX "xref_p9_not_deleted$id" ON case_01.xref_p9_not_deleted USING btree (id);
CREATE INDEX "xref_p9_not_deleted$last" ON case_01.xref_p9_not_deleted USING btree (last);
CREATE INDEX "xref_p9_not_deleted$taxid" ON case_01.xref_p9_not_deleted USING btree (taxid);
CREATE INDEX "xref_p9_not_deleted$upi" ON case_01.xref_p9_not_deleted USING btree (upi);


-- Generated from MySQL SHOW CREATE TABLE for basic-correctness-case-04.
-- Refresh with MySqlBasicCorrectnessFixtureExporter.

-- relation-detector-fixture-table: case_04.act_tool_info
CREATE TABLE `act_tool_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parameters` longtext COLLATE utf8mb4_unicode_ci,
  `result` longtext COLLATE utf8mb4_unicode_ci,
  `tool_call_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `think_act_record_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc219grcvfub4nwn48rauqk1k1` (`think_act_record_id`),
  CONSTRAINT `FKc219grcvfub4nwn48rauqk1k1` FOREIGN KEY (`think_act_record_id`) REFERENCES `think_act_record` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.agent_execution_record
CREATE TABLE `agent_execution_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_description` longtext COLLATE utf8mb4_unicode_ci,
  `agent_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `agent_request` longtext COLLATE utf8mb4_unicode_ci,
  `current_step` int DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `error_message` longtext COLLATE utf8mb4_unicode_ci,
  `max_steps` int DEFAULT NULL,
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` longtext COLLATE utf8mb4_unicode_ci,
  `start_time` datetime(6) DEFAULT NULL,
  `status` enum('FINISHED','IDLE','RUNNING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `step_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `plan_execution_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkdp8uiaw3pmqg7e5gpiao7t6b` (`step_id`),
  KEY `IDXkdp8uiaw3pmqg7e5gpiao7t6b` (`step_id`),
  KEY `FKa2crea2w44iy6u1c1oceoqc3e` (`plan_execution_id`),
  CONSTRAINT `FKa2crea2w44iy6u1c1oceoqc3e` FOREIGN KEY (`plan_execution_id`) REFERENCES `plan_execution_record` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.ai_chat_memory
CREATE TABLE `ai_chat_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `timestamp` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_message_type` CHECK ((`type` in (_utf8mb4'USER',_utf8mb4'ASSISTANT',_utf8mb4'SYSTEM',_utf8mb4'TOOL')))
) ENGINE=InnoDB AUTO_INCREMENT=660 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.coordinator_tools
CREATE TABLE `coordinator_tools` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `access_level` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) NOT NULL,
  `enable_http_service` bit(1) NOT NULL,
  `enable_in_conversation` tinyint(1) NOT NULL DEFAULT '0',
  `enable_internal_toolcall` bit(1) NOT NULL,
  `enable_mcp_service` bit(1) NOT NULL,
  `input_schema` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `plan_template_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_group` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tool_description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tool_name` varchar(3000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `update_time` datetime(6) NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKibttleyf15w8ah324r6ad1nnd` (`plan_template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.cron_task
CREATE TABLE `cron_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) NOT NULL,
  `cron_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cron_time` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_executed_time` datetime(6) DEFAULT NULL,
  `plan_desc` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan_template_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.datasource_config
CREATE TABLE `datasource_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `driver_class_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enable` bit(1) NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdhtr7hj1r51qqvjihf03lk1ot` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.dynamic_agent_tools
CREATE TABLE `dynamic_agent_tools` (
  `agent_id` bigint NOT NULL,
  `tool_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FK7dy1jtd5qhvsh7s3jhei2ip9r` (`agent_id`),
  CONSTRAINT `FK7dy1jtd5qhvsh7s3jhei2ip9r` FOREIGN KEY (`agent_id`) REFERENCES `dynamic_agents` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.dynamic_agents
CREATE TABLE `dynamic_agents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agent_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `built_in` bit(1) DEFAULT NULL,
  `class_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `namespace` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `next_step_prompt` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5l18y2e2ndgsf08xbj2s731ah` (`agent_name`),
  KEY `FK334j7d88lxu7gtgfi7w8rlrj5` (`model_id`),
  CONSTRAINT `FK334j7d88lxu7gtgfi7w8rlrj5` FOREIGN KEY (`model_id`) REFERENCES `dynamic_models` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.dynamic_memories
CREATE TABLE `dynamic_memories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime(6) NOT NULL,
  `memory_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.dynamic_models
CREATE TABLE `dynamic_models` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_key` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `completions_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headers` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `model_description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `temperature` double DEFAULT NULL,
  `topp` double DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.mcp_config
CREATE TABLE `mcp_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `connection_config` varchar(4000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connection_type` enum('SSE','STREAMING','STUDIO') COLLATE utf8mb4_unicode_ci NOT NULL,
  `mcp_server_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi0x39ru038u42jwv6y11hsvbm` (`mcp_server_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.memory_plan_mappings
CREATE TABLE `memory_plan_mappings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `root_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `memory_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpqwh7y7ekcqub7aiqydbk5ebn` (`memory_id`),
  CONSTRAINT `FKpqwh7y7ekcqub7aiqydbk5ebn` FOREIGN KEY (`memory_id`) REFERENCES `dynamic_memories` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.namespace
CREATE TABLE `namespace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `host` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.plan_execution_record
CREATE TABLE `plan_execution_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed` bit(1) DEFAULT NULL,
  `current_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_step_index` int DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parent_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `root_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `summary` longtext COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tool_call_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_request` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5m0efb5wr0yxm2avwnrv5bvf7` (`current_plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.plan_execution_steps
CREATE TABLE `plan_execution_steps` (
  `plan_execution_id` bigint NOT NULL,
  `step` longtext COLLATE utf8mb4_unicode_ci,
  KEY `FKckmddx4nts73omqu8x7aao6u0` (`plan_execution_id`),
  CONSTRAINT `FKckmddx4nts73omqu8x7aao6u0` FOREIGN KEY (`plan_execution_id`) REFERENCES `plan_execution_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.plan_template_version
CREATE TABLE `plan_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) NOT NULL,
  `plan_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan_template_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_index` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.root_task_manager
CREATE TABLE `root_task_manager` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `desired_task_state` enum('CANCEL','PAUSE','RESUME','START','STOP','WAIT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `last_updated` datetime(6) DEFAULT NULL,
  `root_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `task_result` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKs7m19dhyn2sxbijw7dk5iuq78` (`root_plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.system_config
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_group` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_sub_group` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` text COLLATE utf8mb4_unicode_ci,
  `create_time` datetime(6) NOT NULL,
  `default_value` text COLLATE utf8mb4_unicode_ci,
  `description` text COLLATE utf8mb4_unicode_ci,
  `input_type` enum('BOOLEAN','CHECKBOX','NUMBER','SELECT','TEXT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `options_json` text COLLATE utf8mb4_unicode_ci,
  `update_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3olm5aamw31yaxqb37jjavnup` (`config_path`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.think_act_record
CREATE TABLE `think_act_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `error_message` longtext COLLATE utf8mb4_unicode_ci,
  `input_char_count` int DEFAULT NULL,
  `model_context_limit` int DEFAULT NULL,
  `output_char_count` int DEFAULT NULL,
  `parent_execution_id` bigint DEFAULT NULL,
  `think_act_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `think_input` longtext COLLATE utf8mb4_unicode_ci,
  `think_output` longtext COLLATE utf8mb4_unicode_ci,
  `agent_execution_record_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdbj8e2av7j2ou51n8qvata73u` (`agent_execution_record_id`),
  CONSTRAINT `FKdbj8e2av7j2ou51n8qvata73u` FOREIGN KEY (`agent_execution_record_id`) REFERENCES `agent_execution_record` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.user_input_wait_state
CREATE TABLE `user_input_wait_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `current_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `form_description` text COLLATE utf8mb4_unicode_ci,
  `form_inputs` text COLLATE utf8mb4_unicode_ci,
  `last_updated` datetime(6) DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `root_plan_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `waiting` bit(1) NOT NULL,
  `root_task_manager_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7lq9fucrwfx4a0u709ri675kj` (`root_task_manager_id`),
  CONSTRAINT `FK7lq9fucrwfx4a0u709ri675kj` FOREIGN KEY (`root_task_manager_id`) REFERENCES `root_task_manager` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.user_preferences
CREATE TABLE `user_preferences` (
  `user_id` bigint NOT NULL,
  `preference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKepakpib0qnm82vmaiismkqf88` (`user_id`),
  CONSTRAINT `FKepakpib0qnm82vmaiismkqf88` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- relation-detector-fixture-table: case_04.users
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `current_conversation_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `language` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_login` datetime(6) DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =============================================================================
--  旅行日记 · 清库脚本(手动执行,不参与应用启动)
--
--  这条语句会删掉整个数据库,包括所有用户与日记,且不可撤销。
--  只在本地开发环境重建时使用。
--
--  用法:
--    "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -u root -p < src/main/resources/db/reset.sql
--    然后重新执行 schema.sql,再启动应用 —— 演示数据由 DemoDataInitializer 写入。
-- =============================================================================

DROP DATABASE IF EXISTS `travel_diary`;

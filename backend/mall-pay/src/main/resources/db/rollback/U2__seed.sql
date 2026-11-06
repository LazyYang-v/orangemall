-- 反向补偿 V2__seed（mall_pay）：只按主键/唯一键删除种子行，不触碰业务产生的数据。
SET NAMES utf8mb4;
DELETE FROM payment WHERE payment_no IN ('P2120394857100001281', 'P2120394857100002561', 'P2120394857100003841');

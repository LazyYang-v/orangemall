-- 反向补偿 V2__seed（mall_marketing）：只按主键/唯一键删除种子行，不触碰业务产生的数据。
SET NAMES utf8mb4;
DELETE FROM coupon_template WHERE id IN ('CT01', 'CT02', 'CT03', 'CT04', 'CT05', 'CT06', 'CT07', 'CT08');

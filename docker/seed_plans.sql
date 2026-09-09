INSERT INTO package_plans (id, target_role, name, price_monthly, price_yearly, features) 
VALUES 
('FREE', 'TENANT', 'Gói Miễn Phí', 0, 0, '{"swipes_per_day": 15, "boosts": 0}'::jsonb),
('PRO_TENANT', 'TENANT', 'Gói Pro Tìm Bạn & Thuê Phòng', 49000, 490000, '{"swipes_per_day": 50, "boosts": 2}'::jsonb),
('LANDLORD_VIP', 'LANDLORD', 'Gói Chủ Trọ Đẩy Tin VIP', 199000, 1990000, '{"swipes_per_day": 0, "boosts": 5}'::jsonb)
ON CONFLICT (id) DO NOTHING;

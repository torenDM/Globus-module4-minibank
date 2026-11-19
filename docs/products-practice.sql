CREATE TEMP TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name CHAR(255) NOT NULL,
    price BIGINT NOT NULL,
    category CHAR(50) NOT NULL,
    in_stock BOOLEAN NOT NULL,
    date_of_incomming DATE NOT NULL
);

INSERT INTO products (name, price, category, in_stock, date_of_incomming)
VALUES
    ('Ноутбук', 65000, 'Электроника', true, CURRENT_DATE),
    ('Наушники', 4500, 'Электроника', false, CURRENT_DATE),
    ('SQL на практике', 1500, 'Книги', true, CURRENT_DATE - INTERVAL '1 year'),
    ('Основы PostgreSQL', 1200, 'Книги', true, CURRENT_DATE - INTERVAL '6 months'),
    ('Справочник SQL', 1000, 'Книги', true, CURRENT_DATE - INTERVAL '1 month'),
    ('Старое издание', 800, 'Книги', true, CURRENT_DATE - INTERVAL '3 years');

-- Команда 1
SELECT name, price
    FROM products
    WHERE category = 'Электроника' AND in_stock = true;

-- Команда 2
UPDATE products
    SET price = round(price*1.1)::bigint
    WHERE category = 'Книги'
    RETURNING name,price;

-- Команда 3
INSERT INTO products (name, price, category, in_stock, date_of_incomming)
    VALUES ('Моя первая PostgreSQL книга', 500,'Книги',true,CURRENT_DATE)
    RETURNING name,price;

-- Команда 4
DELETE FROM products
       WHERE in_stock = false
       RETURNING name;

-- Команда 5
UPDATE products
    SET price = round(price*0.8)::bigint,
        name = rtrim(name) || ' (Скидочная позиция)'
    WHERE category = 'Книги' AND date_of_incomming BETWEEN (CURRENT_DATE - INTERVAL '2 years')::date
                                                            AND (CURRENT_DATE - INTERVAL '6 months')::date
    RETURNING name, price;
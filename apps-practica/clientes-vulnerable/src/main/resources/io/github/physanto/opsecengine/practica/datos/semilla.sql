-- Datos semilla de la app de práctica. Se cargan al iniciar y al restaurar la base.

DROP TABLE IF EXISTS persona;
DROP TABLE IF EXISTS usuario;

CREATE TABLE usuario (
    usuario    VARCHAR(50)  PRIMARY KEY,
    contrasena VARCHAR(100) NOT NULL,
    rol        VARCHAR(20)  NOT NULL
);

CREATE TABLE persona (
    uid       VARCHAR(20)  PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    documento VARCHAR(20)  NOT NULL,
    correo    VARCHAR(100) NOT NULL
);

INSERT INTO usuario (usuario, contrasena, rol) VALUES
    ('Admin',   'Admin#2026',   'ADMINISTRADOR'),
    ('Cliente', 'Cliente#2026', 'CLIENTE');

INSERT INTO persona (uid, nombre, documento, correo) VALUES
    ('P001', 'Ana María Gómez',      '1010101010', 'ana.gomez@ejemplo.com'),
    ('P002', 'Carlos Pérez Ruiz',    '1020202020', 'carlos.perez@ejemplo.com'),
    ('P003', 'Lucía Fernández',      '1030303030', 'lucia.fernandez@ejemplo.com'),
    ('P004', 'Jorge Iván Muñoz',     '1040404040', 'jorge.munoz@ejemplo.com'),
    ('P005', 'Valentina Castaño',    '1050505050', 'valentina.castano@ejemplo.com');

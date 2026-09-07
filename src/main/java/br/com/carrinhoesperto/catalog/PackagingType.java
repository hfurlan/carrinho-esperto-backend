package br.com.carrinhoesperto.catalog;

/**
 * Canonical packaging type for a product (M004 §10). A small, closed set — the
 * goal is a canonical home for package-type information, not a model of every
 * Brazilian supermarket packaging convention.
 */
public enum PackagingType {
    CARTON,
    BOTTLE,
    CAN,
    BAG,
    BOX,
    TRAY,
    JAR,
    POUCH,
    UNIT,
    OTHER
}

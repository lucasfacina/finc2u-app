package br.com.finc2u.server.shared.seed;

public interface Seeder {

    int order();

    void seed(SeedContext context);

}

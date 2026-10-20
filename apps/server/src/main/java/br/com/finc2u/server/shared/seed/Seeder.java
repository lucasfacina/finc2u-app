package br.com.finc2u.server.shared.seed;

public interface Seeder {

    // Ordem de execução (menor primeiro). Respeita as chaves estrangeiras: quem é referenciado vem antes.
    int order();

    void seed(SeedContext context);

}

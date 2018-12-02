
account{balance:10ether} seller;
account{balance:50ether} customer;

account{contract:"escrow.sol", by:seller} coin("MyCoin");
account{contract:"escrow.sol", by:seller} escrow("Escrow", coin, 1, 5);

coin.transfer(escrow,1) {by:seller};
escrow.start(60minutes) {by:seller};

escrow.() {by:customer, value:5ether};
escrow.close() {by:seller};

uint x;
x = coin.balanceOf(customer) {by:customer};

assert x == 1;
assert seller.balance;
assert customer.balance;


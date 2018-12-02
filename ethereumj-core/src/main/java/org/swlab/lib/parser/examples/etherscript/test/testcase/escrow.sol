pragma solidity ^0.4.8;

contract MyCoin {
    // ���� ���� ����
    string public name; // ��ū �̸�
    string public symbol; // ��ū ����
    uint8 public decimals; // �Ҽ��� ���� �ڸ���
    uint256 public totalSupply; // ��ū �ѷ�
    mapping (address => uint256) public balanceOf; // �� �ּ��� �ܰ�
     
    // �̺�Ʈ �˸�
    event Transfer(address indexed from, address indexed to, uint256 value);

    // ������
    function MyCoin(uint256 _supply, string _name, string _symbol, uint8 _decimals) {
        balanceOf[msg.sender] = _supply;
        name = _name;
        symbol = _symbol;
        decimals = _decimals;
        totalSupply = _supply;
    }
 
// �۱�
    function transfer(address _to, uint256 _value) {
        // ���� �۱� Ȯ��
        if (balanceOf[msg.sender] < _value) throw;
        if (balanceOf[_to] + _value < balanceOf[_to]) throw;

        balanceOf[msg.sender] -= _value;
        balanceOf[_to] += _value;
        Transfer(msg.sender, _to, _value);
    }
}

// (1) ����ũ��
contract Escrow {
    // (2) ���� ����
    MyCoin public token; // ��ū
    uint256 public salesVolume; // �Ǹŷ�
    uint256 public sellingPrice; // �Ǹ� ����
    uint256 public deadline; // ����
    bool public isOpened; // ����ũ�� ���� �÷���
    address public owner; // ������ �ּ�
     
    // (3) �̺�Ʈ �˸�
    event EscrowStart(uint salesVolume, uint sellingPrice, uint deadline, address beneficiary);
    event ConfirmedPayment(address addr, uint amount);
     
    // ������ ���� �޼���� ������
    modifier onlyOwner() { if (msg.sender != owner) throw; _; }

    // (4) ������
    function Escrow (MyCoin _token, uint256 _salesVolume, uint256 _priceInEther) {
        token = MyCoin(_token);
        salesVolume = _salesVolume;
        sellingPrice = _priceInEther * 1 ether;

	owner = msg.sender; // ó���� ����� ������ �ּҸ� �����ڷ� �Ѵ�
    }
     
    // (5) �̸� ���� �Լ�(Ether ����)
    function () payable {
        // ���� �� �Ǵ� ������ ���� ��쿡�� ���� ó��
        if (!isOpened || now >= deadline) throw;
         
        // �Ǹ� ���� �̸��� ��� ���� ó��
        uint amount = msg.value;
        if (amount < sellingPrice) throw;
         
        // ������ ������� ��ū�� �����ϰ� ����ũ�� ���� �÷��׸� false�� ����
        token.transfer(msg.sender, salesVolume);
        isOpened = false;
        ConfirmedPayment(msg.sender, amount);
    }
     
    // (6) ����(��ū�� ���� �� �̻��̶�� ����)
    function start(uint256 _durationInMinutes) onlyOwner {
        if (token == address(0) || salesVolume == 0 || sellingPrice == 0 || deadline != 0) throw;
        if (token.balanceOf(this) >= salesVolume){
            deadline = now + _durationInMinutes * 1 minutes;
            isOpened = true;
            EscrowStart(salesVolume, sellingPrice, deadline, owner);  
        }
    }
     
    // (7) ���� �ð� Ȯ�ο� �޼���(�� ����)
    function getRemainingTime() constant returns(uint min) {
        if(now < deadline) {
            min = (deadline - now) / (1 minutes);
        }
    }
     
    // (8) ����
    function close() onlyOwner {
        // ��ū�� �����ڿ��� ����
        token.transfer(owner, token.balanceOf(this));
        // ����� �ı�(�ش� ����� �����ϰ� �ִ� Ether�� �����ڿ��� ����
        selfdestruct(owner);
    }
}

#include <iostream>

int main(){
   
    int x = 5;
    int y = 0;
	
    std::cout << "Hello " << x/y << "World!" << 'oups\n';

    return 0;
}

//g++ ./main.cpp -Wall -Wextra -o prog
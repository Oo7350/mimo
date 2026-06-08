class Test:
    c=21
    def print_n(self):
        c=20
        self.c+=20
        print(c)
test=Test()
test.print_n()
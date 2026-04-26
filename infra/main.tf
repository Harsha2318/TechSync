provider "aws" {
  region = var.region
}

resource "aws_instance" "techsync" {
  ami           = var.ami_id
  instance_type = var.instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [aws_security_group.techsync_sg.id]

  tags = {
    Name = "TechSync"
  }
}